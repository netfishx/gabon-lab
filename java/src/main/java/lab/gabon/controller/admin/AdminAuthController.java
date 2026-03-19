package lab.gabon.controller.admin;

import jakarta.validation.Valid;
import lab.gabon.common.ApiResponse;
import lab.gabon.model.request.AdminRequests.AdminLoginRequest;
import lab.gabon.model.request.AdminRequests.AdminRefreshRequest;
import lab.gabon.model.response.AdminResponses.AdminMeResponse;
import lab.gabon.model.response.AdminResponses.AdminTokenPairResponse;
import lab.gabon.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/v1/auth")
public class AdminAuthController {

  private final AdminService adminService;

  public AdminAuthController(AdminService adminService) {
    this.adminService = adminService;
  }

  @PostMapping("/login")
  public ApiResponse<AdminTokenPairResponse> login(@Valid @RequestBody AdminLoginRequest req) {
    var result = adminService.adminLogin(req.username(), req.password());
    return ApiResponse.ok(result);
  }

  @PostMapping("/refresh")
  public ApiResponse<AdminTokenPairResponse> refresh(@Valid @RequestBody AdminRefreshRequest req) {
    var result = adminService.adminRefresh(req.refreshToken());
    return ApiResponse.ok(result);
  }

  @PostMapping("/logout")
  public ApiResponse<Void> logout(
      @RequestHeader("Authorization") String authHeader,
      @Valid @RequestBody AdminRefreshRequest req) {
    var accessToken = authHeader.substring("Bearer ".length());
    adminService.adminLogout(accessToken, req.refreshToken());
    return ApiResponse.ok();
  }

  @GetMapping("/me")
  public ApiResponse<AdminMeResponse> me(@RequestAttribute("userId") long userId) {
    var result = adminService.getAdminMe(userId);
    return ApiResponse.ok(result);
  }
}
