package com.gabon.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /**
     * 添加拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/error",
                    "/favicon.ico",
                    "/actuator/**",
                    "/doc.html",
                    // 根路径Swagger UI (优先级最高)
                    "/swagger-ui",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/swagger-resources/**",
                    "/v3/api-docs/**",
                    "/webjars/**",
                    // 支持context path的Swagger路径 (备用)
                    "/admin/swagger-ui",
                    "/admin/swagger-ui/**",
                    "/admin/swagger-ui.html",
                    "/admin/swagger-resources/**",
                    "/admin/v3/api-docs/**",
                    "/admin/webjars/**"
                );
    }


    /**
     * 跨域配置
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
