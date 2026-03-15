package com.gabon.service.service;

import com.gabon.service.model.dto.ChangePasswordRequest;
import com.gabon.service.model.dto.LoginRequest;
import com.gabon.service.model.dto.LoginResponse;
import com.gabon.service.model.dto.RefreshTokenResponse;
import com.gabon.service.model.dto.RegisterRequest;
import com.gabon.service.model.entity.Customer;

/**
 * 客户认证服务
 */
public interface AuthService {

    /**
     * 客户登录
     * @param request 包含用户名和密码的登录请求
     * @param clientIp 客户端IP地址
     * @return 包含JWT令牌和客户信息的登录响应
     */
    LoginResponse login(LoginRequest request, String clientIp);

    /**
     * 客户注册
     * @param request 包含用户名、密码、确认密码的注册请求
     * @param clientIp 客户端IP地址
     * @return 包含JWT令牌和客户信息的登录响应（注册成功后自动登录）
     */
    LoginResponse register(RegisterRequest request, String clientIp);

    /**
     * 客户登出
     * @param token JWT令牌
     */
    void logout(String token);

    /**
     * 验证JWT令牌并获取客户信息
     * @param token JWT令牌
     * @return 如果令牌有效返回客户实体，否则返回null
     */
    Customer validateToken(String token);

    /**
     * 从令牌获取当前客户信息
     * @param token JWT令牌
     * @return 客户实体
     */
    Customer getCurrentCustomer(String token);

    /**
     * 修改密码
     * @param request 包含旧密码、新密码、确认密码的请求
     * @param customerId 当前客户ID（从Token中获取）
     */
    void changePassword(ChangePasswordRequest request, Long customerId);

    /**
     * 刷新JWT令牌
     * @param token 当前JWT令牌（可以是已过期的）
     * @return 包含新令牌和过期时间的响应
     */
    RefreshTokenResponse refreshToken(String token);
}
