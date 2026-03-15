package com.gabon.service.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gabon.common.enums.VideoStatusEnum;
import com.gabon.common.config.MediaConvertConfig;
import com.gabon.common.service.MediaConvertService;
import com.gabon.common.service.S3Service;
import com.gabon.service.mapper.VideoMapper;
import com.gabon.service.model.entity.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.mediaconvert.model.Job;
import software.amazon.awssdk.services.mediaconvert.model.JobStatus;

import java.util.List;

/**
 * MediaConvert 转码状态轮询调度器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaConvertScheduler {

    private final VideoMapper videoMapper;
    private final MediaConvertService mediaConvertService;
    private final MediaConvertConfig mediaConvertConfig;
    private final S3Service s3Service;

    /**
     * 每30秒检查一次“处理中”的视频
     */
    @Scheduled(fixedDelay = 30000)
    public void checkTranscodeStatus() {
        // Query videos with status TRANSCODING
        List<Video> videos = videoMapper.selectList(new LambdaQueryWrapper<Video>()
                .eq(Video::getStatus, VideoStatusEnum.TRANSCODING)
                .isNotNull(Video::getTranscodeJobId));

        if (videos.isEmpty()) {
            return;
        }

        for (Video video : videos) {
            processVideo(video);
        }
    }

    private void processVideo(Video video) {
        try {
            String jobId = video.getTranscodeJobId();
            Job job = mediaConvertService.getJobStatus(jobId);
            JobStatus status = job.status();

            if (status == JobStatus.COMPLETE) {
                handleComplete(video, job);
            } else if (status == JobStatus.ERROR || status == JobStatus.CANCELED) {
                handleError(video, job);
            }
            // else: SUBMITTED, PROGRESSING -> do nothing
        } catch (Exception e) {
            log.error("Error checking transcode status for videoId: {}, jobId: {}", video.getId(),
                    video.getTranscodeJobId(), e);
        }
    }

    private void handleComplete(Video video, Job job) {
        // 输出路径: gabon/videos/preview/{customerId}/
        // 输出文件: {源文件名}.gif + {源文件名}.0000000.jpg
        String storagePath = video.getStoragePath();
        String fileName = storagePath.substring(storagePath.lastIndexOf("/") + 1);
        String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));

        String outputPrefix = String.format("%s%d/", mediaConvertConfig.getPreviewPrefix(), video.getCustomerId());
        String gifKey = outputPrefix + nameWithoutExt + ".gif";
        String thumbnailKey = outputPrefix + nameWithoutExt + ".0000000.jpg";

        video.setPreviewGifUrl(s3Service.buildPublicUrl(gifKey));
        video.setThumbnailUrl(s3Service.buildPublicUrl(thumbnailKey));
        video.setStatus(VideoStatusEnum.PENDING_REVIEW);
        videoMapper.updateById(video);

        log.info("Transcode completed - Video ID: {}, JobId: {}, GIF: {}, Thumbnail: {}",
                video.getId(), job.id(), gifKey, thumbnailKey);
    }

    private void handleError(Video video, Job job) {
        video.setStatus(VideoStatusEnum.FAILED);
        videoMapper.updateById(video);
        log.warn("Transcode failed loop - Video ID: {}, JobId: {}, Status: {}", video.getId(), job.id(), job.status());
    }

}
