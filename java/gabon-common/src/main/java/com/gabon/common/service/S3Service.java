package com.gabon.common.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * S3服务接口
 * S3 Service Interface
 */
public interface S3Service {

    /**
     * 上传文件到S3
     * 
     * @param file   文件
     * @param folder 文件夹路径 (例如: "images", "videos")
     * @return S3文件URL
     */
    String uploadFile(MultipartFile file, String folder);

    /**
     * 删除S3文件
     * 
     * @param fileUrl 文件URL
     * @return 是否成功
     */
    boolean deleteFile(String fileUrl);

    /**
     * 生成预签名URL (用于临时访问私有文件)
     *
     * @param key             S3 key
     * @param durationMinutes 有效期(分钟)
     * @return 预签名URL
     */
    String generatePresignedUrl(String key, int durationMinutes);

    /**
     * 生成预签名PUT URL (用于前端直传S3)
     *
     * @param key             S3 key
     * @param contentType     文件MIME类型
     * @param durationMinutes 有效期(分钟)
     * @return 预签名PUT URL
     */
    String generatePresignedUploadUrl(String key, String contentType, int durationMinutes);

    /**
     * 构建S3公开访问URL
     *
     * @param key S3 key
     * @return 公开访问URL
     */
    String buildPublicUrl(String key);
}
