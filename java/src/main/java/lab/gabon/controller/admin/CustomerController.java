package lab.gabon.controller.admin;

import jakarta.validation.Valid;
import lab.gabon.common.ApiResponse;
import lab.gabon.common.PageResponse;
import lab.gabon.model.request.AdminRequests.ResetCustomerPasswordRequest;
import lab.gabon.model.response.AdminResponses.CustomerListItemResponse;
import lab.gabon.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/v1/customers")
public class CustomerController {

  private final AdminService adminService;

  public CustomerController(AdminService adminService) {
    this.adminService = adminService;
  }

  @GetMapping
  public ApiResponse<PageResponse<CustomerListItemResponse>> list(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String search) {
    var result = adminService.listCustomers(page, pageSize, search);
    return ApiResponse.ok(result);
  }

  @PutMapping("/{id}/password")
  public ApiResponse<Void> resetPassword(
      @PathVariable long id, @Valid @RequestBody ResetCustomerPasswordRequest req) {
    adminService.resetCustomerPassword(id, req.newPassword());
    return ApiResponse.ok();
  }
}
