package com.gabon.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabon.admin.model.dto.LoginRequest;
import com.gabon.admin.model.dto.LoginResponse;
import com.gabon.admin.model.entity.AdminUser;
import com.gabon.admin.service.AuthService;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.exception.BizException;
import com.gabon.common.util.JsonData;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "账户登录、登出、令牌管理等认证相关接口")
public class AuthController {

    private final AuthService authService;

    /**
     * 账户登录
     */
    @Operation(summary = "账户登录", description = "使用账户名和密码进行登录认证，成功后返回JWT令牌和账户信息")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "登录成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "401", description = "账户名或密码错误")
    })
    @PostMapping("/login")
    public JsonData<LoginResponse> login(
            @Parameter(description = "登录请求信息 (必填) | Login request info (required)", required = true) @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        LoginResponse response = authService.login(request, clientIp);
        return JsonData.buildSuccess(response);
    }

    /**
     * 账户登出
     */
    @Operation(summary = "账户登出", description = "账户主动登出，清除服务端缓存的令牌信息", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "登出成功"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @PostMapping("/logout")
    public JsonData<Void> logout(
            @Parameter(description = "认证令牌 (必填) | Authentication token (required)", required = true, example = "Bearer eyJhbGciOiJIUzI1NiJ9...") @RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        authService.logout(token);
        return JsonData.buildSuccess();
    }

    /**
     * 获取当前账户信息
     */
    @Operation(summary = "获取当前账户信息", description = "根据令牌获取当前登录账户的详细信息", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取账户信息成功"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @GetMapping("/me")
    public JsonData<LoginResponse.UserInfo> getCurrentUser(
            @Parameter(description = "认证令牌 (必填) | Authentication token (required)", required = true, example = "Bearer eyJhbGciOiJIUzI1NiJ9...") @RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        AdminUser user = authService.getCurrentUser(token);
        if (user == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .build();

        return JsonData.buildSuccess(userInfo);
    }

    /**
     * 从Authorization header中提取token
     */
    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

}
