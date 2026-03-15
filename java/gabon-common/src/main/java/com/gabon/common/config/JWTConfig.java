package com.gabon.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JWTConfig {

    /**
     * JWT密钥
     */
    private String secret = "journey-default-secret-change-in-production";

    /**
     * 令牌前缀
     */
    private String tokenPrefix = "Bearer ";

    /**
     * 过期时间（毫秒），默认7天
     */
    private Long expireTime = 1000L * 60 * 60 * 24 * 7;

    /**
     * 主题
     */
    private String subject = "cabbage";
}
