package lab.gabon.controller.admin;

import jakarta.validation.Valid;
import lab.gabon.common.ApiResponse;
import lab.gabon.common.PageResponse;
import lab.gabon.model.request.AdminRequests.ReviewVideoRequest;
import lab.gabon.model.response.AdminResponses.AdminVideoResponse;
import lab.gabon.service.AdminService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/v1/videos")
public class VideoReviewController {

  private final AdminService adminService;

  public VideoReviewController(AdminService adminService) {
    this.adminService = adminService;
  }

  @GetMapping
  public ApiResponse<PageResponse<AdminVideoResponse>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) Short status) {
    var result = adminService.listVideosForReview(page, pageSize, status);
    return ApiResponse.ok(result);
  }

  @GetMapping("/{id}")
  public ApiResponse<AdminVideoResponse> detail(@PathVariable long id) {
    var result = adminService.getVideoForReview(id);
    return ApiResponse.ok(result);
  }

  @PostMapping("/{id}/review")
  public ApiResponse<Void> review(
      @PathVariable long id,
      @RequestAttribute("userId") long adminId,
      @Valid @RequestBody ReviewVideoRequest req) {
    adminService.reviewVideo(id, adminId, req.status(), req.reviewNotes());
    return ApiResponse.ok();
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable long id) {
    adminService.adminDeleteVideo(id);
    return ApiResponse.ok();
  }
}
