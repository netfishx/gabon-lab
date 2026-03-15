package com.gabon.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 安全工具类
 * Security Utility Class
 * 
 * Provides common security-related utility methods for extracting
 * user information from HTTP requests.
 */
public class SecurityUtil {

    /**
     * 从请求中提取JWT Token
     * Extract JWT token from HTTP request
     * 
     * @param request HTTP请求
     * @return JWT token，如果不存在则返回null
     */
    public static String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    /**
     * 从请求中获取当前用户ID
     * Get current user ID from HTTP request
     * 
     * 此方法需要配合具体的AuthService使用。
     * 调用者需要先使用 extractToken() 获取token，
     * 然后通过AuthService获取用户信息。
     * 
     * @param request         HTTP请求
     * @param userIdExtractor 用户ID提取函数（通常是 token ->
     *                        authService.getCurrentUser(token).getId()）
     * @return 当前用户ID
     * @throws RuntimeException 如果无法获取用户信息
     */
    public static Long getCurrentUserId(HttpServletRequest request,
            java.util.function.Function<String, Long> userIdExtractor) {
        String token = extractToken(request);
        if (token != null) {
            Long userId = userIdExtractor.apply(token);
            if (userId != null) {
                return userId;
            }
        }
        throw new RuntimeException("无法获取当前用户信息");
    }

    /**
     * 获取当前用户的用户名
     * Get current username from HTTP request
     * 
     * @param request           HTTP request containing the authorization token
     * @param usernameExtractor Function to extract username from token
     * @return Current username
     * @throws RuntimeException if unable to get current user information
     */
    public static String getCurrentUsername(HttpServletRequest request,
            java.util.function.Function<String, String> usernameExtractor) {
        String token = extractToken(request);
        if (token != null) {
            String username = usernameExtractor.apply(token);
            if (username != null) {
                return username;
            }
        }
        return "unknown";
    }
}
