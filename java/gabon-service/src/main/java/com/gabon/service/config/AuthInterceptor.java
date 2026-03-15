package com.gabon.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gabon.common.util.JsonData;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 客户认证拦截器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PathMatcher pathMatcher = new AntPathMatcher();
    
    // 用于存储当前客户的ThreadLocal
    public static ThreadLocal<Customer> threadLocal = new ThreadLocal<>();

    // 完全不需要认证的路径（不解析token）
    private static final List<String> EXCLUDE_PATHS = Arrays.asList(
        "/service/api/auth/login",
        "/service/api/auth/register",
        "/service/api/auth/refresh",
        "/service/api/videos",  // 首页视频列表（公开接口，游客可访问）
        "/service/api/videos/featured",  // 热点视频列表（公开接口，游客可访问）
        "/service/api/videos/search",  // 首页视频搜索（公开接口，游客可访问）
        "/service/api/videos/featured/search",  // 热点视频搜索（公开接口，游客可访问）
        "/api/videos",
        "/api/videos/featured",
        "/api/videos/search",  // 首页视频搜索（公开接口，游客可访问）
        "/api/videos/featured/search",  // 热点视频搜索（公开接口，游客可访问）
        "/error",
        "/favicon.ico",
        "/actuator/**",
        "/doc.html",
        // Swagger UI路径
        "/swagger-ui",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/swagger-resources/**",
        "/v3/api-docs/**",
        "/webjars/**",
        // 支持context path的Swagger路径
        "/service/swagger-ui",
        "/service/swagger-ui/**",
        "/service/swagger-ui.html",
        "/service/swagger-resources/**",
        "/service/v3/api-docs/**",
        "/service/webjars/**"
    );

    // 可选认证的路径（不需要强制认证，但如果提供了token会解析并设置用户信息）
    private static final List<String> OPTIONAL_AUTH_PATHS = Arrays.asList(
        "/service/api/videos/*/detail",  // 视频详情（公开接口，游客可访问，提供token会解析用户信息）
        "/api/videos/*/detail",  // 视频详情（公开接口，游客可访问，提供token会解析用户信息）
        "/service/api/videos/*/play-click",  // 播放点击（游客+登录用户，登录后记录用户ID）
        "/api/videos/*/play-click",
        "/service/api/videos/*/play-valid",  // 有效播放（游客+登录用户，登录后更新任务进度）
        "/api/videos/*/play-valid",
        "/service/api/videos/user/*",  // 获取他人作品列表（公开接口，可选认证）
        "/api/videos/user/*",  // 获取他人作品列表（公开接口，可选认证）
        "/service/api/customer/*/profile",  // 获取他人主页信息（公开接口，可选认证）
        "/api/customer/*/profile",  // 获取他人主页信息（公开接口，可选认证）
        "/service/api/customer/*/follow",  // 获取他人关注/粉丝列表（公开接口，可选认证）
        "/api/customer/*/follow",  // 获取他人关注/粉丝列表（公开接口，可选认证）
        "/service/api/ads/watch",  // 获取广告（公开接口，可选认证）
        "/api/ads/watch"  // 获取广告（公开接口，可选认证）
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        log.debug("AuthInterceptor - Request URI: {}, Method: {}", requestURI, method);

        // 跳过完全排除的路径
        if (isExcludePath(requestURI) || "OPTIONS".equals(method)) {
            log.debug("AuthInterceptor - Skipping authentication for: {}", requestURI);
            return true;
        }

        // 处理可选认证的路径（如果提供了token就解析，但不强制要求）
        if (isOptionalAuthPath(requestURI)) {
            String authorization = request.getHeader("Authorization");
            if (StringUtils.hasText(authorization)) {
                String token = extractToken(authorization);
                if (StringUtils.hasText(token)) {
                    // 尝试解析token，如果有效则设置用户信息
                    Customer customer = authService.validateToken(token);
                    if (customer != null) {
                        threadLocal.set(customer);
                        request.setAttribute("customerId", customer.getId());
                        request.setAttribute("username", customer.getUsername());
                        log.debug("AuthInterceptor - Optional auth: User {} authenticated for: {}", customer.getUsername(), requestURI);
                    } else {
                        log.debug("AuthInterceptor - Optional auth: Invalid token for: {}", requestURI);
                    }
                }
            } else {
                log.debug("AuthInterceptor - Optional auth: No token provided for: {}", requestURI);
            }
            // 无论是否有token，都允许访问
            return true;
        }

        // 其他路径需要强制认证
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization)) {
            writeUnauthorizedResponse(response, "缺少认证令牌");
            return false;
        }

        // 提取token
        String token = extractToken(authorization);
        if (!StringUtils.hasText(token)) {
            writeUnauthorizedResponse(response, "令牌格式无效");
            return false;
        }

        // 验证token
        Customer customer = authService.validateToken(token);
        if (customer == null) {
            writeUnauthorizedResponse(response, "令牌无效或已过期");
            return false;
        }

        // 将客户信息存储到ThreadLocal和请求属性中
        threadLocal.set(customer);
        request.setAttribute("customerId", customer.getId());
        request.setAttribute("username", customer.getUsername());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清理ThreadLocal以防止内存泄漏
        threadLocal.remove();
    }

    /**
     * 检查路径是否需要完全排除认证（不解析token）
     */
    private boolean isExcludePath(String requestURI) {
        return EXCLUDE_PATHS.stream().anyMatch(excludePath -> {
            if (excludePath.endsWith("/**")) {
                String prefix = excludePath.substring(0, excludePath.length() - 3);
                return requestURI.startsWith(prefix);
            } else if (excludePath.contains("*")) {
                // 支持中间通配符，如 /api/videos/*/play-click
                return pathMatcher.match(excludePath, requestURI);
            } else {
                return requestURI.equals(excludePath);
            }
        });
    }

    /**
     * 检查路径是否为可选认证路径（如果提供了token会解析，但不强制要求）
     */
    private boolean isOptionalAuthPath(String requestURI) {
        return OPTIONAL_AUTH_PATHS.stream().anyMatch(optionalPath -> {
            if (optionalPath.contains("*")) {
                // 使用 AntPathMatcher 匹配通配符路径
                return pathMatcher.match(optionalPath, requestURI);
            } else {
                return requestURI.equals(optionalPath);
            }
        });
    }

    /**
     * 从Authorization请求头中提取token
     */
    private String extractToken(String authorization) {
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }

    /**
     * 写入未授权响应
     */
    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        
        JsonData<Void> result = JsonData.buildCodeAndMsg(401, message);
        String json = objectMapper.writeValueAsString(result);
        response.getWriter().write(json);
    }
}
