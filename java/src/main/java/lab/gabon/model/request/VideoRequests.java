package lab.gabon.model.request;

import jakarta.validation.constraints.NotBlank;

public final class VideoRequests {

  private VideoRequests() {}

  public record UploadUrlRequest(
      @NotBlank String fileName, long fileSize, @NotBlank String mimeType) {}

  public record ConfirmUploadRequest(
      @NotBlank String fileName,
      @NotBlank String title,
      String description,
      long fileSize,
      @NotBlank String mimeType,
      Integer duration,
      Integer width,
      Integer height) {}

  public record ReviewVideoRequest(short status, String reviewNotes) {}
}
