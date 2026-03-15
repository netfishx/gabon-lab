package com.gabon.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabon.admin.model.entity.AdminUser;
import com.gabon.admin.service.AuthService;
import com.gabon.common.util.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 认证拦截器
 * Authentication Interceptor
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ThreadLocal to store current admin user
    public static ThreadLocal<AdminUser> threadLocal = new ThreadLocal<>();

    // Paths that don't require authentication
    private static final List<String> EXCLUDE_PATHS = Arrays.asList(
            "/api/auth/login",
            "/error",
            "/favicon.ico",
            "/actuator/**",
            "/doc.html",
            // Swagger UI paths
            "/swagger-ui",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/v3/api-docs/**",
            "/webjars/**",
            // Context path Swagger paths
            "/admin/swagger-ui",
            "/admin/swagger-ui/**",
            "/admin/swagger-ui.html",
            "/admin/swagger-resources/**",
            "/admin/v3/api-docs/**",
            "/admin/webjars/**");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        log.debug("AuthInterceptor - Request URI: {}, Method: {}", requestURI, method);

        // Skip authentication for excluded paths
        if (isExcludePath(requestURI) || "OPTIONS".equals(method)) {
            log.debug("AuthInterceptor - Skipping authentication for: {}", requestURI);
            return true;
        }

        // Get Authorization header
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization)) {
            writeUnauthorizedResponse(response, "缺少认证令牌");
            return false;
        }

        // Extract token
        String token = extractToken(authorization);
        if (!StringUtils.hasText(token)) {
            writeUnauthorizedResponse(response, "无效的认证令牌格式");
            return false;
        }

        // Validate token
        AdminUser user = authService.validateToken(token);
        if (user == null) {
            writeUnauthorizedResponse(response, "认证令牌无效或已过期");
            return false;
        }

        // Store user info in ThreadLocal and request attributes
        threadLocal.set(user);
        request.setAttribute("userId", user.getId());
        request.setAttribute("username", user.getUsername());
        request.setAttribute("userRole", user.getRole());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        // Clean up ThreadLocal to prevent memory leaks
        threadLocal.remove();
    }

    /**
     * 检查是否为排除路径
     */
    private boolean isExcludePath(String requestURI) {
        return EXCLUDE_PATHS.stream().anyMatch(excludePath -> {
            if (excludePath.endsWith("/**")) {
                String prefix = excludePath.substring(0, excludePath.length() - 3);
                return requestURI.startsWith(prefix);
            } else {
                return requestURI.equals(excludePath);
            }
        });
    }

    /**
     * 从Authorization header中提取token
     */
    private String extractToken(String authorization) {
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }

    /**
     * 写入未认证响应
     */
    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        JsonData<Void> result = JsonData.buildCodeAndMsg(401, message);
        String json = objectMapper.writeValueAsString(result);
        response.getWriter().write(json);
    }
}
