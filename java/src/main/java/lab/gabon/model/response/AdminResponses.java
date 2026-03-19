package lab.gabon.model.response;

import java.time.Instant;
import lab.gabon.common.PageResponse;

public final class AdminResponses {

  private AdminResponses() {}

  public record AdminTokenPairResponse(String accessToken, String refreshToken) {}

  public record AdminMeResponse(
      long id,
      String username,
      short role,
      String fullName,
      String phone,
      String avatarUrl,
      short status) {}

  public record AdminUserResponse(
      long id,
      String username,
      short role,
      String fullName,
      String phone,
      String avatarUrl,
      short status,
      Instant lastLoginAt,
      Instant createdAt) {}

  public record AdminUserListResponse(PageResponse<AdminUserResponse> page) {}

  // -- Customer Management --------------------------------------------------

  public record CustomerListItemResponse(
      long id,
      String username,
      String name,
      String phone,
      String email,
      boolean isVip,
      long diamondBalance,
      Instant createdAt) {}

  // -- Video Review ---------------------------------------------------------

  public record AdminVideoResponse(
      long id,
      long customerId,
      String title,
      String fileUrl,
      short status,
      String reviewNotes,
      Long reviewedBy,
      Instant reviewedAt,
      long totalClicks,
      long likeCount,
      Instant createdAt) {}

  // -- Reports --------------------------------------------------------------

  public record RevenueReportItem(String date, long totalDiamonds) {}

  public record DailyVideoReportItem(String date, long count) {}

  public record VideoSummaryItem(short status, long count) {}
}
