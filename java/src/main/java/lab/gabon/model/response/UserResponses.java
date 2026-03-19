package lab.gabon.model.response;

public final class UserResponses {

  private UserResponses() {}

  public record UserProfileResponse(
      long id,
      String username,
      String name,
      String phone,
      String email,
      String avatarUrl,
      String signature,
      boolean isVip,
      long diamondBalance,
      long followingCount,
      long followerCount) {}

  public record PublicUserResponse(
      long id,
      String username,
      String name,
      String avatarUrl,
      String signature,
      boolean isVip,
      long followingCount,
      long followerCount,
      boolean isFollowing) {}

  public record FollowListItemResponse(long id, String username, String name, String avatarUrl) {}
}
