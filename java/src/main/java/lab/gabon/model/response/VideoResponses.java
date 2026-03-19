package lab.gabon.model.response;

import java.time.Instant;

public final class VideoResponses {

  private VideoResponses() {}

  public record VideoDetailResponse(
      long id,
      long customerId,
      String customerName,
      String customerAvatarUrl,
      String title,
      String description,
      String fileUrl,
      String thumbnailUrl,
      short status,
      long totalClicks,
      long validClicks,
      long likeCount,
      boolean liked,
      Instant createdAt) {}

  public record VideoListItemResponse(
      long id,
      long customerId,
      String customerName,
      String customerAvatarUrl,
      String title,
      String fileUrl,
      String thumbnailUrl,
      long likeCount,
      long totalClicks,
      Instant createdAt) {}

  public record UploadUrlResponse(String uploadUrl, String fileUrl) {}
}
