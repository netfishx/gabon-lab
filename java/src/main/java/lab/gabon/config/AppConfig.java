package lab.gabon.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppConfig(JwtConfig jwt, S3Config s3) {

  public record JwtConfig(
      String customerSecret,
      Duration customerAccessTtl,
      Duration customerRefreshTtl,
      String adminSecret,
      Duration adminAccessTtl,
      Duration adminRefreshTtl,
      String currentKid) {}

  public record S3Config(
      String endpoint,
      String region,
      String accessKey,
      String secretKey,
      String bucketVideos,
      String bucketAvatars) {}
}
