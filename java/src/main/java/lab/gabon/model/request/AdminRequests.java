package lab.gabon.model.request;

import jakarta.validation.constraints.NotBlank;

public final class AdminRequests {

  private AdminRequests() {}

  public record AdminLoginRequest(@NotBlank String username, @NotBlank String password) {}

  public record AdminRefreshRequest(@NotBlank String refreshToken) {}

  public record CreateAdminRequest(
      @NotBlank String username,
      @NotBlank String password,
      short role,
      String fullName,
      String phone) {}

  public record UpdateAdminRequest(String fullName, String phone, Short role, Short status) {}

  public record ResetPasswordRequest(@NotBlank String newPassword) {}
}
