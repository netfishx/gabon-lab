package lab.gabon.service;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import lab.gabon.config.AppConfig;
import lab.gabon.model.response.VideoResponses.UploadUrlResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class StorageService {

  private static final Logger log = LoggerFactory.getLogger(StorageService.class);

  private final AppConfig.S3Config config;
  private final S3Presigner presigner;
  private final boolean stubMode;

  public StorageService(AppConfig appConfig) {
    this.config = appConfig.s3();

    if (config.endpoint() == null || config.endpoint().isBlank()) {
      log.info("S3 endpoint is blank — running in stub mode");
      this.presigner = null;
      this.stubMode = true;
    } else {
      this.presigner =
          S3Presigner.builder()
              .endpointOverride(URI.create(config.endpoint()))
              .region(Region.of(config.region()))
              .credentialsProvider(
                  StaticCredentialsProvider.create(
                      AwsBasicCredentials.create(config.accessKey(), config.secretKey())))
              .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
              .build();
      this.stubMode = false;
    }
  }

  public UploadUrlResponse generateVideoUploadUrl(String fileName) {
    return generateUploadUrl(config.bucketVideos(), "videos", fileName);
  }

  public UploadUrlResponse generateAvatarUploadUrl(String fileName) {
    return generateUploadUrl(config.bucketAvatars(), "avatars", fileName);
  }

  private UploadUrlResponse generateUploadUrl(String bucket, String prefix, String fileName) {
    var ext = extractExtension(fileName);
    var key = prefix + "/" + UUID.randomUUID() + "." + ext;

    if (stubMode) {
      return new UploadUrlResponse(
          "https://stub.local/" + bucket + "/" + key + "?upload=true",
          "https://stub.local/" + bucket + "/" + key);
    }

    var presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15))
            .putObjectRequest(b -> b.bucket(bucket).key(key))
            .build();

    var presigned = presigner.presignPutObject(presignRequest);
    var uploadUrl = presigned.url().toString();
    var fileUrl = buildPublicUrl(bucket, key);
    return new UploadUrlResponse(uploadUrl, fileUrl);
  }

  private String buildPublicUrl(String bucket, String key) {
    var endpoint = config.endpoint().replaceAll("/+$", "");
    return endpoint + "/" + bucket + "/" + key;
  }

  private static String extractExtension(String fileName) {
    var dot = fileName.lastIndexOf('.');
    return dot >= 0 ? fileName.substring(dot + 1) : "bin";
  }
}
