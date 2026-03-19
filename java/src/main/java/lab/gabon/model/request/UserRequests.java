package lab.gabon.model.request;

import jakarta.validation.constraints.NotBlank;

public final class UserRequests {

  private UserRequests() {}

  public record UpdateProfileRequest(String name, String phone, String email, String signature) {}

  public record AvatarConfirmRequest(@NotBlank String avatarUrl) {}
}
