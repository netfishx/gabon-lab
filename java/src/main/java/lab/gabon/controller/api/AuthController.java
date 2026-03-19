package lab.gabon.controller.api;

import jakarta.validation.Valid;
import lab.gabon.common.ApiResponse;
import lab.gabon.model.request.AuthRequests.LoginRequest;
import lab.gabon.model.request.AuthRequests.RefreshRequest;
import lab.gabon.model.request.AuthRequests.RegisterRequest;
import lab.gabon.model.request.AuthRequests.UpdatePasswordRequest;
import lab.gabon.model.response.AuthResponses.CustomerMeResponse;
import lab.gabon.model.response.AuthResponses.TokenPairResponse;
import lab.gabon.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<TokenPairResponse> register(@Valid @RequestBody RegisterRequest req) {
    var result = authService.register(req.username(), req.password());
    return ApiResponse.ok(result);
  }

  @PostMapping("/login")
  public ApiResponse<TokenPairResponse> login(@Valid @RequestBody LoginRequest req) {
    var result = authService.login(req.username(), req.password());
    return ApiResponse.ok(result);
  }

  @PostMapping("/refresh")
  public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest req) {
    var result = authService.refresh(req.refreshToken());
    return ApiResponse.ok(result);
  }

  @PostMapping("/logout")
  public ApiResponse<Void> logout(
      @RequestHeader("Authorization") String authHeader, @Valid @RequestBody RefreshRequest req) {
    var accessToken = authHeader.substring("Bearer ".length());
    authService.logout(accessToken, req.refreshToken());
    return ApiResponse.ok();
  }

  @GetMapping("/me")
  public ApiResponse<CustomerMeResponse> me(@RequestAttribute("userId") long userId) {
    var result = authService.getMe(userId);
    return ApiResponse.ok(result);
  }

  @PutMapping("/password")
  public ApiResponse<Void> updatePassword(
      @RequestAttribute("userId") long userId, @Valid @RequestBody UpdatePasswordRequest req) {
    authService.updatePassword(userId, req.currentPassword(), req.newPassword());
    return ApiResponse.ok();
  }
}
