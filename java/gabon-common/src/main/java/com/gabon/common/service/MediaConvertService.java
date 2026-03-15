package com.gabon.common.service;

/**
 * MediaConvert 转码服务接口
 * MediaConvert Transcode Service Interface
 */
public interface MediaConvertService {

    /**
     * 创建转码任务（使用 AWS MediaConvert 模板，自定义输出路径）
     *
     * @param sourceS3Key 源文件S3 key
     * @param outputS3Prefix 输出目录S3 key前缀，如 gabon/videos/preview/123/
     * @return MediaConvert Job ID
     */
    String createTranscodeJob(String sourceS3Key, String outputS3Prefix);

    /**
     * 获取转码任务状态
     * 
     * @param jobId 任务ID
     * @return 任务详情（包含状态和输出）
     */
    software.amazon.awssdk.services.mediaconvert.model.Job getJobStatus(String jobId);
}
