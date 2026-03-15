package com.gabon.common.service.impl;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.gabon.common.config.AwsS3Config;
import com.gabon.common.service.S3Service;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.exception.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3服务实现
 * S3 Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsS3Config awsS3Config;

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        try {
            // 1. 验证文件
            if (file == null || file.isEmpty()) {
                throw new BizException(BizCodeEnum.FILE_NOT_EXIST);
            }

            // 2. 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String fileName = UUID.randomUUID().toString() + extension;
            String key = folder + "/" + fileName;

            // 3. 获取文件内容类型
            String contentType = file.getContentType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            // 4. 上传到S3
            // Note: ACL removed - use bucket policy or CloudFront for public access
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(awsS3Config.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    // .acl(ObjectCannedACL.PUBLIC_READ) // Removed: Bucket has ACLs disabled
                    .build();

            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // 5. 构建直接访问URL
            String fileUrl = buildPublicUrl(key);

            log.info("文件上传成功 - Bucket: {}, Key: {}, URL: {}",
                    awsS3Config.getBucketName(), key, fileUrl);

            return fileUrl;

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage(), e);
            throw new BizException(500, "文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public boolean deleteFile(String fileUrl) {
        try {
            // 从URL提取S3 key
            String key = extractKeyFromUrl(fileUrl);
            if (key == null) {
                log.warn("无法从URL提取key: {}", fileUrl);
                return false;
            }

            // 删除S3对象
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(awsS3Config.getBucketName())
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);

            log.info("文件删除成功 - Key: {}", key);
            return true;

        } catch (Exception e) {
            log.error("文件删除失败 - URL: {}, 错误: {}", fileUrl, e.getMessage());
            return false;
        }
    }

    @Override
    public String generatePresignedUrl(String key, int durationMinutes) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(awsS3Config.getBucketName())
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(durationMinutes))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

            return presignedRequest.url().toString();

        } catch (Exception e) {
            log.error("生成预签名URL失败 - Key: {}, 错误: {}", key, e.getMessage());
            throw new BizException(500, "生成预签名URL失败");
        }
    }

    @Override
    public String generatePresignedUploadUrl(String key, String contentType, int durationMinutes) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(awsS3Config.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .build();

            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(durationMinutes))
                    .putObjectRequest(putObjectRequest)
                    .build();

            PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

            return presignedRequest.url().toString();

        } catch (Exception e) {
            log.error("生成上传预签名URL失败 - Key: {}, 错误: {}", key, e.getMessage());
            throw new BizException(500, "生成上传预签名URL失败");
        }
    }

    @Override
    public String buildPublicUrl(String key) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                awsS3Config.getBucketName(),
                awsS3Config.getRegion(),
                key);
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }

    /**
     * 从URL提取S3 key
     */
    private String extractKeyFromUrl(String fileUrl) {
        try {
            // URL format: https://bucket.s3.region.amazonaws.com/folder/file.ext
            String bucketPrefix = String.format("https://%s.s3.%s.amazonaws.com/",
                    awsS3Config.getBucketName(),
                    awsS3Config.getRegion());

            if (fileUrl.startsWith(bucketPrefix)) {
                return fileUrl.substring(bucketPrefix.length());
            }

            // Alternative format: https://s3.region.amazonaws.com/bucket/folder/file.ext
            String alternativePrefix = String.format("https://s3.%s.amazonaws.com/%s/",
                    awsS3Config.getRegion(),
                    awsS3Config.getBucketName());

            if (fileUrl.startsWith(alternativePrefix)) {
                return fileUrl.substring(alternativePrefix.length());
            }

            return null;
        } catch (Exception e) {
            log.error("提取key失败: {}", e.getMessage());
            return null;
        }
    }
}
