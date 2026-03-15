package com.gabon.service.controller;

import com.gabon.service.model.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.exception.BizException;
import com.gabon.common.util.JsonData;
import com.gabon.service.config.AuthInterceptor;
import com.gabon.service.model.dto.LoginResponse;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.service.AuthService;

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
 * 客户认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "账户登录、登出、令牌管理等认证相关接口")
public class AuthController {
    @Autowired
    AuthService authService;

    /**
     * 客户登录
     */
    @Operation(
        summary = "账户登录",
        description = "使用账户名和密码进行登录认证，成功后返回JWT令牌和账户信息",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "登录成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "401", description = "账户名或密码错误")
    })
    @PostMapping("/login")
    public JsonData<LoginResponse> login(
        @Parameter(description = "登录请求信息 (必填) | Login request info (required)", required = true, example = "Bearer eyJhbGciOiJIUzI1NiJ9...")
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest) {
        
        String clientIp = getClientIp(httpRequest);
        LoginResponse response = authService.login(request, clientIp);
        return JsonData.buildSuccess(response);
    }

    /**
     * 客户注册
     */
    @Operation(
        summary = "账户注册",
        description = "新用户注册，注册成功后自动登录并返回JWT令牌和账户信息",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "注册成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误（用户名已存在、密码不一致等）")
    })
    @PostMapping("/register")
    public JsonData<LoginResponse> register(
        @Parameter(description = "注册请求信息 (必填) | Register request info (required)", required = true)
        @Valid @RequestBody RegisterRequest request,
        HttpServletRequest httpRequest) {

        String clientIp = getClientIp(httpRequest);
        LoginResponse response = authService.register(request, clientIp);
        return JsonData.buildSuccess(response);
    }

    /**
     * 客户登出
     */
    @Operation(
        summary = "账户登出",
        description = "账户主动登出，清除服务端缓存的令牌信息",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "登出成功"),
        @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @PostMapping("/logout")
    public JsonData<Void> logout(
        @Parameter(description = "JWT token", required = true, example = "Bearer eyJhbGciOiJIUzI1NiJ9...")
        @RequestHeader("Authorization") String authorization) {
        
        String token = extractToken(authorization);
        authService.logout(token);
        return JsonData.buildSuccess();
    }

    /**
     * 获取当前账户信息
     */
    @Operation(
        summary = "获取当前账户信息",
        description = "根据令牌获取当前登录账户的详细信息",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "获取账户信息成功"),
        @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @GetMapping("/me")
    public JsonData<LoginResponse.CustomerInfo> getCurrentCustomer(
        @Parameter(description = "认证令牌 (必填) | Authentication token (required)", required = true, example = "Bearer eyJhbGciOiJIUzI1NiJ9...")
        @RequestHeader("Authorization") String authorization) {
        
        String token = extractToken(authorization);
        Customer customer = authService.getCurrentCustomer(token);

        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        LoginResponse.CustomerInfo customerInfo = new LoginResponse.CustomerInfo(
                customer.getId(),
                customer.getUsername(),
                customer.getName(),
                customer.getPhone(),
                customer.getIsVip(),
                customer.getAvatarUrl()
        );

        return JsonData.buildSuccess(customerInfo);
    }

    /**
     * 修改密码
     */
    @Operation(
        summary = "修改密码",
        description = "修改当前登录账户的密码，需要验证旧密码",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "密码修改成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误（旧密码错误、新密码不一致等）"),
        @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @PutMapping("/password")
    public JsonData<Void> changePassword(
        @Parameter(description = "修改密码请求信息 (必填) | Change password request info (required)", required = true)
        @Valid @RequestBody ChangePasswordRequest request,
        @RequestHeader("Authorization") String authorization) {
        
        // 从Token中获取当前用户ID（通过拦截器已设置到ThreadLocal）
        Customer customer = AuthInterceptor.threadLocal.get();
        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }
        
        authService.changePassword(request, customer.getId());
        return JsonData.buildSuccess();
    }

    /**
     * 刷新令牌
     */
    @Operation(
        summary = "刷新令牌",
        description = "刷新JWT令牌，延长登录状态，即使令牌已过期也可以刷新（在刷新窗口内）",
        security = @SecurityRequirement(name = "Bearer")
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "令牌刷新成功"),
        @ApiResponse(responseCode = "401", description = "令牌无效或用户不存在")
    })
    @PostMapping("/refresh")
    public JsonData<RefreshTokenResponse> refreshToken(
        @Parameter(description = "认证令牌 (必填，可以是已过期的) | Authentication token (required, can be expired)", required = true, example = "Bearer eyJhbGciOiJIUzI1NiJ9...")
        @RequestHeader("Authorization") String authorization) {
        
        String token = extractToken(authorization);
        RefreshTokenResponse response = authService.refreshToken(token);
        return JsonData.buildSuccess(response);
    }

    /**
     * 从Authorization请求头中提取token
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
