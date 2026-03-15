package com.gabon.service.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gabon.common.enums.VideoStatusEnum;
import com.gabon.service.mapper.CustomerMapper;
import com.gabon.service.mapper.VideoMapper;
import com.gabon.service.model.dto.VideoConfirmUploadRequest;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.model.entity.Video;
import com.gabon.service.service.VideoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
public class VideoUploadIntegrationTest {

    @Autowired
    private VideoService videoService;

    @Autowired
    private com.gabon.service.scheduler.MediaConvertScheduler mediaConvertScheduler;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private VideoMapper videoMapper;

    private Long testCustomerId;

    // S3 上已存在的测试视频，格式须为: gabon/videos/source/{customerId}/xxx.mp4
    private static final String TEST_S3_KEY = "gabon/videos/source/1/f5ac446b-52c7-455e-b05f-7562d325567d.mp4";
    private static final long TEST_FILE_SIZE = 1024000L;

    @BeforeEach
    public void setup() {
        Customer customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>().last("LIMIT 1"));
        if (customer == null) {
            customer = new Customer();
            customer.setName("IntegrationTestUser");
            customer.setPhone("12345678901");
            customer.setIsVip(0);
            customerMapper.insert(customer);
        }
        testCustomerId = customer.getId();
        System.out.println("Using Customer ID: " + testCustomerId);
    }

    @Test
    public void testFullVideoUploadFlow() {
        // 1. 用 S3 上已有的视频直接确认上传
        VideoConfirmUploadRequest confirmRequest = new VideoConfirmUploadRequest();
        confirmRequest.setFileName("integration_test_" + System.currentTimeMillis() + ".mp4");
        confirmRequest.setFileSize(TEST_FILE_SIZE);
        confirmRequest.setMimeType("video/mp4");
        confirmRequest.setTitle("Integration Test Video");
        confirmRequest.setS3Key(TEST_S3_KEY);

        Video video = videoService.confirmVideoUpload(testCustomerId, confirmRequest);

        // 2. 断言转码任务已创建（状态应为 TRANSCODING=2）
        assertNotNull(video, "Video object should be returned");
        assertNotNull(video.getId(), "Video ID should be generated");
        assertEquals(VideoStatusEnum.TRANSCODING, video.getStatus(),
                "Status should be TRANSCODING after successful transcode job creation");
        assertNotNull(video.getTranscodeJobId(), "Transcode Job ID should be present");

        System.out.println("Video confirmed. ID: " + video.getId());
        System.out.println("Transcode Job ID: " + video.getTranscodeJobId());

        // 3. 轮询等待转码完成（最多约2分钟）
        //    转码完成后状态应为 PENDING_REVIEW=3
        int maxRetries = 40;
        boolean completed = false;

        for (int i = 0; i < maxRetries; i++) {
            System.out.println("Polling attempt " + (i + 1) + "...");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            try {
                mediaConvertScheduler.checkTranscodeStatus();
            } catch (Exception e) {
                System.out.println("Scheduler check warning: " + e.getMessage());
            }

            Video updatedVideo = videoMapper.selectById(video.getId());

            if (updatedVideo.getStatus() == VideoStatusEnum.PENDING_REVIEW) {
                System.out.println("Transcode COMPLETED! Status is now PENDING_REVIEW.");
                System.out.println("Video URL: " + updatedVideo.getFileUrl());
                System.out.println("Thumbnail URL: " + updatedVideo.getThumbnailUrl());
                completed = true;
                break;
            } else if (updatedVideo.getStatus() == VideoStatusEnum.FAILED) {
                fail("Transcode job FAILED. Check application logs.");
            }
        }

        assertTrue(completed, "Transcode did not complete within timeout period");
    }
}
