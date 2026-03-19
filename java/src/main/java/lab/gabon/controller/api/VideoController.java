package lab.gabon.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lab.gabon.common.ApiResponse;
import lab.gabon.common.AppError;
import lab.gabon.common.AppException;
import lab.gabon.common.PageResponse;
import lab.gabon.model.request.VideoRequests.ConfirmUploadRequest;
import lab.gabon.model.request.VideoRequests.UploadUrlRequest;
import lab.gabon.model.response.VideoResponses.UploadUrlResponse;
import lab.gabon.model.response.VideoResponses.VideoDetailResponse;
import lab.gabon.model.response.VideoResponses.VideoListItemResponse;
import lab.gabon.service.VideoService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class VideoController {

  private final VideoService videoService;

  public VideoController(VideoService videoService) {
    this.videoService = videoService;
  }

  @GetMapping("/videos")
  public ApiResponse<PageResponse<VideoListItemResponse>> listVideos(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
    return videoService.listVideos(page, pageSize);
  }

  @GetMapping("/videos/featured")
  public ApiResponse<PageResponse<VideoListItemResponse>> listFeatured(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int pageSize) {
    return videoService.listFeatured(page, pageSize);
  }

  @GetMapping("/videos/me")
  public ApiResponse<PageResponse<VideoListItemResponse>> listMyVideos(
      HttpServletRequest request,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int pageSize) {
    long customerId = getUserId(request);
    return videoService.listMyVideos(customerId, page, pageSize);
  }

  @PostMapping("/videos/upload-url")
  public ApiResponse<UploadUrlResponse> generateUploadUrl(
      HttpServletRequest request, @Valid @RequestBody UploadUrlRequest req) {
    long customerId = getUserId(request);
    return videoService.generateUploadUrl(customerId, req);
  }

  @PostMapping("/videos/confirm-upload")
  public ApiResponse<VideoDetailResponse> confirmUpload(
      HttpServletRequest request, @Valid @RequestBody ConfirmUploadRequest req) {
    long customerId = getUserId(request);
    return videoService.confirmUpload(customerId, req);
  }

  @GetMapping("/videos/{id}")
  public ApiResponse<VideoDetailResponse> getVideo(
      HttpServletRequest request, @PathVariable long id) {
    Long customerId = getOptionalUserId(request);
    return videoService.getVideo(id, customerId);
  }

  @DeleteMapping("/videos/{id}")
  public ApiResponse<Void> deleteVideo(HttpServletRequest request, @PathVariable long id) {
    long customerId = getUserId(request);
    return videoService.deleteVideo(id, customerId);
  }

  @PostMapping("/videos/{id}/like")
  public ApiResponse<Void> likeVideo(HttpServletRequest request, @PathVariable long id) {
    long customerId = getUserId(request);
    return videoService.likeVideo(id, customerId);
  }

  @DeleteMapping("/videos/{id}/like")
  public ApiResponse<Void> unlikeVideo(HttpServletRequest request, @PathVariable long id) {
    long customerId = getUserId(request);
    return videoService.unlikeVideo(id, customerId);
  }

  @PostMapping("/videos/{id}/play-click")
  public ApiResponse<Void> playClick(HttpServletRequest request, @PathVariable long id) {
    Long customerId = getOptionalUserId(request);
    String ipAddress = request.getRemoteAddr();
    return videoService.recordPlayClick(id, customerId, ipAddress);
  }

  @PostMapping("/videos/{id}/play-valid")
  public ApiResponse<Void> playValid(HttpServletRequest request, @PathVariable long id) {
    Long customerId = getOptionalUserId(request);
    String ipAddress = request.getRemoteAddr();
    return videoService.recordPlayValid(id, customerId, ipAddress);
  }

  @GetMapping("/users/{id}/videos")
  public ApiResponse<PageResponse<VideoListItemResponse>> listUserVideos(
      @PathVariable long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int pageSize) {
    return videoService.listUserVideos(id, page, pageSize);
  }

  // -- Helpers ------------------------------------------------------------

  private static long getUserId(HttpServletRequest request) {
    var attr = request.getAttribute("userId");
    if (attr == null) {
      throw new AppException(new AppError.Unauthorized());
    }
    return (long) attr;
  }

  private static Long getOptionalUserId(HttpServletRequest request) {
    var attr = request.getAttribute("userId");
    return attr != null ? (Long) attr : null;
  }
}
