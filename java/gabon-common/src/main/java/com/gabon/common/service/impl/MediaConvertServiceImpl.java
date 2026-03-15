package com.gabon.common.service.impl;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.gabon.common.config.AwsS3Config;
import com.gabon.common.config.MediaConvertConfig;
import com.gabon.common.exception.BizException;
import com.gabon.common.service.MediaConvertService;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.mediaconvert.MediaConvertClient;
import software.amazon.awssdk.services.mediaconvert.model.CreateJobRequest;
import software.amazon.awssdk.services.mediaconvert.model.CreateJobResponse;
import software.amazon.awssdk.services.mediaconvert.model.GetJobRequest;
import software.amazon.awssdk.services.mediaconvert.model.GetJobResponse;
import software.amazon.awssdk.services.mediaconvert.model.FileGroupSettings;
import software.amazon.awssdk.services.mediaconvert.model.Input;
import software.amazon.awssdk.services.mediaconvert.model.Job;
import software.amazon.awssdk.services.mediaconvert.model.JobSettings;
import software.amazon.awssdk.services.mediaconvert.model.OutputGroup;
import software.amazon.awssdk.services.mediaconvert.model.OutputGroupSettings;
import software.amazon.awssdk.services.mediaconvert.model.OutputGroupType;

/**
 * MediaConvert 转码服务实现
 * MediaConvert Transcode Service Implementation
 */
@Slf4j
@Service
public class MediaConvertServiceImpl implements MediaConvertService {

        private final MediaConvertClient mediaConvertClient;
        private final MediaConvertConfig mediaConvertConfig;
        private final AwsS3Config awsS3Config;

        public MediaConvertServiceImpl(@Lazy MediaConvertClient mediaConvertClient,
                        MediaConvertConfig mediaConvertConfig,
                        AwsS3Config awsS3Config) {
                this.mediaConvertClient = mediaConvertClient;
                this.mediaConvertConfig = mediaConvertConfig;
                this.awsS3Config = awsS3Config;
        }

        @Override
        public String createTranscodeJob(String sourceS3Key, String outputS3Prefix) {
                try {
                        String inputUri = String.format("s3://%s/%s", awsS3Config.getBucketName(), sourceS3Key);
                        String outputUri = String.format("s3://%s/%s", awsS3Config.getBucketName(), outputS3Prefix);

                        // 覆盖模板的输出地址
                        OutputGroup outputGroup = OutputGroup.builder()
                                        .outputGroupSettings(OutputGroupSettings.builder()
                                                        .type(OutputGroupType.FILE_GROUP_SETTINGS)
                                                        .fileGroupSettings(FileGroupSettings.builder()
                                                                        .destination(outputUri)
                                                                        .build())
                                                        .build())
                                        .build();

                        CreateJobRequest jobRequest = CreateJobRequest.builder()
                                        .role(mediaConvertConfig.getRoleArn())
                                        .jobTemplate(mediaConvertConfig.getJobTemplateName())
                                        .settings(JobSettings.builder()
                                                        .inputs(Input.builder()
                                                                        .fileInput(inputUri)
                                                                        .build())
                                                        .outputGroups(outputGroup)
                                                        .build())
                                        .build();

                        CreateJobResponse response = mediaConvertClient.createJob(jobRequest);
                        String jobId = response.job().id();

                        log.info("MediaConvert job created - JobId: {}, Source: {}, Output: {}", jobId, inputUri, outputUri);

                        return jobId;

                } catch (Exception e) {
                        log.error("创建转码任务失败 - Source: {}, 错误: {}", sourceS3Key, e.getMessage(), e);
                        throw new BizException(500, "创建转码任务失败: " + e.getMessage());
                }
        }

        @Override
        public Job getJobStatus(String jobId) {
                try {
                        GetJobRequest getJobRequest = GetJobRequest.builder()
                                        .id(jobId)
                                        .build();

                        GetJobResponse response = mediaConvertClient.getJob(getJobRequest);
                        return response.job();
                } catch (Exception e) {
                        log.error("获取转码任务状态失败 - JobId: {}, 错误: {}", jobId, e.getMessage());
                        throw new BizException(500, "获取转码任务状态失败");
                }
        }
}
