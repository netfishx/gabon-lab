package lab.gabon.controller.admin;

import jakarta.validation.Valid;
import lab.gabon.common.ApiResponse;
import lab.gabon.common.PageResponse;
import lab.gabon.model.request.AdminRequests.CreateAdminRequest;
import lab.gabon.model.request.AdminRequests.ResetPasswordRequest;
import lab.gabon.model.request.AdminRequests.UpdateAdminRequest;
import lab.gabon.model.response.AdminResponses.AdminUserResponse;
import lab.gabon.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/v1/admin-users")
public class AdminUserController {

  private final AdminService adminService;

  public AdminUserController(AdminService adminService) {
    this.adminService = adminService;
  }

  @GetMapping
  public ApiResponse<PageResponse<AdminUserResponse>> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
    var result = adminService.listAdminUsers(page, pageSize);
    return ApiResponse.ok(result);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<AdminUserResponse> create(@Valid @RequestBody CreateAdminRequest req) {
    var result =
        adminService.createAdminUser(
            req.username(), req.password(), req.role(), req.fullName(), req.phone());
    return ApiResponse.ok(result);
  }

  @GetMapping("/{id}")
  public ApiResponse<AdminUserResponse> detail(@PathVariable long id) {
    var result = adminService.getAdminUser(id);
    return ApiResponse.ok(result);
  }

  @PutMapping("/{id}")
  public ApiResponse<Void> update(
      @PathVariable long id, @Valid @RequestBody UpdateAdminRequest req) {
    adminService.updateAdminUser(id, req.fullName(), req.phone(), req.role(), req.status());
    return ApiResponse.ok();
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable long id) {
    adminService.deleteAdminUser(id);
    return ApiResponse.ok();
  }

  @PutMapping("/{id}/password")
  public ApiResponse<Void> resetPassword(
      @PathVariable long id, @Valid @RequestBody ResetPasswordRequest req) {
    adminService.resetAdminPassword(id, req.newPassword());
    return ApiResponse.ok();
  }
}
