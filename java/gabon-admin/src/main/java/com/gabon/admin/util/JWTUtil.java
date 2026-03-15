package com.gabon.admin.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理员认证JWT工具类
 */
@Slf4j
@Component
public class JWTUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-time:604800000}") // 默认7天（毫秒）
    private Long expireTime;

    /**
     * 为管理员生成JWT令牌
     */
    public String generateToken(Long adminUserId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("adminUserId", adminUserId);
        claims.put("username", username);
        claims.put("type", "admin");

        Instant now = Instant.now();
        Instant expiryDate = now.plusMillis(expireTime);

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiryDate))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析并验证JWT令牌
     */
    public Claims parseToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.error("Failed to parse JWT token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从令牌获取管理员用户ID
     */
    public Long getAdminUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            return claims.get("adminUserId", Long.class);
        }
        return null;
    }

    /**
     * 从令牌获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            return claims.getSubject();
        }
        return null;
    }

    /**
     * 验证令牌
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            if (claims == null) {
                return false;
            }

            // 检查过期时间
            Date expiry = claims.getExpiration();
            return expiry.after(new Date());
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从令牌获取过期时间
     */
    public Instant getExpirationFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            return claims.getExpiration().toInstant();
        }
        return null;
    }
}
