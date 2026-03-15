package com.gabon.admin.service.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gabon.admin.mapper.AdminUserMapper;
import com.gabon.admin.model.dto.LoginRequest;
import com.gabon.admin.model.dto.LoginResponse;
import com.gabon.admin.model.entity.AdminUser;
import com.gabon.admin.service.AuthService;
import com.gabon.admin.util.JWTUtil;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.exception.BizException;
import com.gabon.common.util.PasswordUtil;

import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 认证服务实现
 * Authentication Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AdminUserMapper adminUserMapper;
    @Autowired
    JWTUtil jwtUtil;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        // 1. Query admin user
        AdminUser user = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getUsername, request.getUsername())
                        .isNull(AdminUser::getDeletedFlag));

        if (user == null) {
            log.warn("Login failed: username not found - {}", request.getUsername());
            throw new BizException(BizCodeEnum.ACCOUNT_PWD_ERROR);
        }

        // Check account status
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("Login failed: account disabled - {}", request.getUsername());
            throw new BizException(BizCodeEnum.ACCOUNT_PWD_ERROR);
        }

        // 2. Verify password
        if (!PasswordUtil.verifyPassword(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: incorrect password - username: {}", request.getUsername());
            throw new BizException(BizCodeEnum.ACCOUNT_PWD_ERROR);
        }

        // 3. Update last login time
        user.setLastLoginAt(Instant.now());
        adminUserMapper.updateById(user);

        // 4. Generate JWT token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        Instant expiresAt = jwtUtil.getExpirationFromToken(token);

        // 5. Build and return response
        LoginResponse response = buildLoginResponse(user, token, expiresAt);

        log.info("Admin user login successful - User ID: {}, Role: {}, IP: {}", user.getId(), user.getRole(), clientIp);
        return response;
    }

    @Override
    public void logout(String token) {
        // Simplified logout - just log the action
        log.info("Admin user logout");
    }

    @Override
    public AdminUser validateToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            return null;
        }

        Long adminUserId = jwtUtil.getAdminUserIdFromToken(token);
        if (adminUserId == null) {
            return null;
        }

        // Get admin user from database
        AdminUser user = adminUserMapper.selectOne(
                new LambdaQueryWrapper<AdminUser>()
                        .eq(AdminUser::getId, adminUserId)
                        .isNull(AdminUser::getDeletedFlag));

        return user;
    }

    @Override
    public boolean isLoggedIn(String token) {
        return validateToken(token) != null;
    }

    @Override
    public AdminUser getCurrentUser(String token) {
        return validateToken(token);
    }

    /**
     * Build login response
     * 
     * @param user      Admin user entity
     * @param token     JWT token
     * @param expiresAt Token expiration time
     * @return Login response
     */
    private LoginResponse buildLoginResponse(AdminUser user, String token, Instant expiresAt) {

        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .build();

        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresAt(expiresAt)
                .userInfo(userInfo)
                .build();
    }
}
