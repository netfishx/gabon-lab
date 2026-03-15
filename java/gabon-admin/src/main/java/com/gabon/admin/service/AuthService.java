package com.gabon.admin.service;

import com.gabon.admin.model.dto.LoginRequest;
import com.gabon.admin.model.dto.LoginResponse;
import com.gabon.admin.model.entity.AdminUser;

/**
 * 认证服务接口
 * Authentication Service Interface
 */
public interface AuthService {

    /**
     * 账户登录
     * 
     * @param request  登录请求
     * @param clientIp 客户端IP
     * @return 登录响应，包含令牌和账户信息
     */
    LoginResponse login(LoginRequest request, String clientIp);

    /**
     * 账户登出
     * 
     * @param token JWT令牌
     */
    void logout(String token);

    /**
     * 验证令牌有效性
     * 
     * @param token JWT令牌
     * @return 账户实体
     */
    AdminUser validateToken(String token);

    /**
     * 检查账户是否已登录
     * 
     * @param token JWT令牌
     * @return true if已登录
     */
    boolean isLoggedIn(String token);

    /**
     * 获取当前登录账户
     * 
     * @param token JWT令牌
     * @return 账户实体
     */
    AdminUser getCurrentUser(String token);
}
