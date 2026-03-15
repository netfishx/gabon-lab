package com.gabon.service.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
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
 * 客户认证JWT工具类
 */
@Slf4j
@Component
public class JWTUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400}") // 默认24小时（秒）
    private Long expiration;

    /**
     * 为客户生成JWT令牌
     */
    public String generateToken(Long customerId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("customerId", customerId);
        claims.put("username", username);
        claims.put("type", "customer");

        Instant now = Instant.now();
        Instant expiryDate = now.plusSeconds(expiration);

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
     * 解析JWT令牌但忽略过期时间（仅用于刷新令牌场景）
     * 即使 token 已过期，只要签名有效就能返回 claims
     */
    public Claims parseTokenIgnoreExpiry(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            // Token is expired but signature is valid — still return the claims for refresh
            // use
            log.debug("Token expired but signature valid, returning claims for refresh: {}", e.getMessage());
            return e.getClaims();
        } catch (Exception e) {
            log.error("Failed to parse JWT token (ignoreExpiry): {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从令牌获取客户ID
     */
    public Long getCustomerIdFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            return claims.get("customerId", Long.class);
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
