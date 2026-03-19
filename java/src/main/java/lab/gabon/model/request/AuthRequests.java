package lab.gabon.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {

  private AuthRequests() {}

  public record RegisterRequest(
      @NotBlank @Size(min = 3, max = 50) String username,
      @NotBlank @Size(min = 6, max = 100) String password) {}

  public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

  public record RefreshRequest(@NotBlank String refreshToken) {}

  public record UpdatePasswordRequest(
      @NotBlank String currentPassword, @NotBlank @Size(min = 6, max = 100) String newPassword) {}
}
