package lab.gabon.model.response;

public final class AuthResponses {

  private AuthResponses() {}

  public record TokenPairResponse(String accessToken, String refreshToken) {}

  public record CustomerMeResponse(
      long id,
      String username,
      String name,
      String phone,
      String email,
      String avatarUrl,
      String signature,
      boolean isVip,
      long diamondBalance) {}
}
