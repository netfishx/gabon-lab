package lab.gabon.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lab.gabon.common.ApiResponse;
import lab.gabon.common.AppError;
import lab.gabon.common.AppException;
import lab.gabon.common.PageResponse;
import lab.gabon.model.request.UserRequests.AvatarConfirmRequest;
import lab.gabon.model.request.UserRequests.UpdateProfileRequest;
import lab.gabon.model.response.UserResponses.FollowListItemResponse;
import lab.gabon.model.response.UserResponses.PublicUserResponse;
import lab.gabon.model.response.UserResponses.UserProfileResponse;
import lab.gabon.model.response.VideoResponses.UploadUrlResponse;
import lab.gabon.service.UserService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  // -- My Profile -------------------------------------------------------------

  @GetMapping("/me/profile")
  public ApiResponse<UserProfileResponse> getMyProfile(HttpServletRequest request) {
    long userId = getUserId(request);
    return ApiResponse.ok(userService.getMyProfile(userId));
  }

  @PutMapping("/me/profile")
  public ApiResponse<UserProfileResponse> updateMyProfile(
      HttpServletRequest request, @Valid @RequestBody UpdateProfileRequest req) {
    long userId = getUserId(request);
    return ApiResponse.ok(userService.updateMyProfile(userId, req));
  }

  // -- Avatar -----------------------------------------------------------------

  @PostMapping("/me/avatar-upload-url")
  public ApiResponse<UploadUrlResponse> getAvatarUploadUrl(HttpServletRequest request) {
    long userId = getUserId(request);
    return ApiResponse.ok(userService.generateAvatarUploadUrl(userId));
  }

  @PostMapping("/me/avatar/confirm")
  public ApiResponse<Void> confirmAvatarUpload(
      HttpServletRequest request, @Valid @RequestBody AvatarConfirmRequest req) {
    long userId = getUserId(request);
    userService.confirmAvatarUpload(userId, req.avatarUrl());
    return ApiResponse.ok();
  }

  // -- Public Profile ---------------------------------------------------------

  @GetMapping("/{id}")
  public ApiResponse<PublicUserResponse> getUserProfile(
      HttpServletRequest request, @PathVariable long id) {
    Long currentUserId = getOptionalUserId(request);
    return ApiResponse.ok(userService.getUserProfile(id, currentUserId));
  }

  // -- Follow / Unfollow ------------------------------------------------------

  @PostMapping("/{id}/follow")
  public ApiResponse<Void> follow(HttpServletRequest request, @PathVariable long id) {
    long userId = getUserId(request);
    userService.follow(userId, id);
    return ApiResponse.ok();
  }

  @DeleteMapping("/{id}/follow")
  public ApiResponse<Void> unfollow(HttpServletRequest request, @PathVariable long id) {
    long userId = getUserId(request);
    userService.unfollow(userId, id);
    return ApiResponse.ok();
  }

  // -- Following / Followers --------------------------------------------------

  @GetMapping("/{id}/following")
  public ApiResponse<PageResponse<FollowListItemResponse>> getFollowing(
      @PathVariable long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int pageSize) {
    return ApiResponse.ok(userService.getFollowing(id, page, pageSize));
  }

  @GetMapping("/{id}/followers")
  public ApiResponse<PageResponse<FollowListItemResponse>> getFollowers(
      @PathVariable long id,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int pageSize) {
    return ApiResponse.ok(userService.getFollowers(id, page, pageSize));
  }

  // -- Helpers ----------------------------------------------------------------

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
