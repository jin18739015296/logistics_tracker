package com.logistics.api.util;

import com.logistics.api.service.TokenRedisService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtUtils {

    // 访问令牌配置
    @Value("${jwt.access.secret}")
    private String accessSecret;

    @Value("${jwt.access.expiration}")
    private long accessExpiration;

    // 刷新令牌配置
    @Value("${jwt.refresh.secret}")
    private String refreshSecret;

    @Value("${jwt.refresh.expiration}")
    private long refreshExpiration;

    // 令牌元数据
    @Value("${jwt.issuer:logistics-platform}")
    private String issuer;

    @Value("${jwt.audience:logistics-platform-api}")
    private String audience;

    @Autowired
    private TokenRedisService tokenRedisService;

    // 获取访问令牌的签名密钥
    private SecretKey getAccessSigningKey() {
        return Keys.hmacShaKeyFor(accessSecret.getBytes(StandardCharsets.UTF_8));
    }

    // 获取刷新令牌的签名密钥
    private SecretKey getRefreshSigningKey() {
        return Keys.hmacShaKeyFor(refreshSecret.getBytes(StandardCharsets.UTF_8));
    }

    // 生成访问令牌
    public String generateAccessToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("type", "access");
        
        String token = generateToken(claims, username, accessExpiration, getAccessSigningKey());
        
        // 存储到 Redis
        tokenRedisService.storeAccessToken(token, username, accessExpiration);
        log.info("访问令牌已生成并存储: username={}, expiration={}ms", username, accessExpiration);
        
        return token;
    }

    // 生成刷新令牌
    public String generateRefreshToken(String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        
        String token = generateToken(claims, username, refreshExpiration, getRefreshSigningKey());
        
        // 存储到 Redis
        tokenRedisService.storeRefreshToken(token, username, refreshExpiration);
        log.info("刷新令牌已生成并存储: username={}, expiration={}ms", username, refreshExpiration);
        
        return token;
    }

    // 生成令牌
    private String generateToken(Map<String, Object> claims, String subject, long expirationTime, SecretKey key) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    // 从访问令牌中获取用户名
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getAccessSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    // 从刷新令牌中获取用户名
    public String getUsernameFromRefreshToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getRefreshSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    // 从令牌中获取角色
    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getAccessSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return (String) claims.get("role");
    }

    // 从令牌中获取类型
    public String getTokenType(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getAccessSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return (String) claims.get("type");
        } catch (Exception e) {
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(getRefreshSigningKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                return (String) claims.get("type");
            } catch (Exception ex) {
                return null;
            }
        }
    }

    // 验证访问令牌（包括 Redis 检查）
    public boolean validateAccessToken(String token) {
        try {
            // 1. 验证 JWT 签名和过期时间
            Jwts.parser()
                    .verifyWith(getAccessSigningKey())
                    .build()
                    .parseSignedClaims(token);
            
            // 2. 验证是否在 Redis 中存在且不在黑名单
            boolean valid = tokenRedisService.validateAccessToken(token);
            if (!valid) {
                log.warn("访问令牌验证失败: 令牌不存在或已被列入黑名单");
            }
            return valid;
        } catch (Exception e) {
            log.warn("访问令牌验证失败: {}", e.getMessage());
            return false;
        }
    }

    // 验证刷新令牌（包括 Redis 检查）
    public boolean validateRefreshToken(String token) {
        try {
            // 1. 验证 JWT 签名和过期时间（使用刷新令牌的密钥）
            Jwts.parser()
                    .verifyWith(getRefreshSigningKey())
                    .build()
                    .parseSignedClaims(token);
            
            // 2. 验证是否在 Redis 中存在且不在黑名单
            boolean valid = tokenRedisService.validateRefreshToken(token);
            if (!valid) {
                log.warn("刷新令牌验证失败: 令牌不存在或已被列入黑名单");
            }
            return valid;
        } catch (Exception e) {
            log.warn("刷新令牌验证失败: {}", e.getMessage());
            return false;
        }
    }

    // 使令牌失效（加入黑名单）
    public void invalidateToken(String token) {
        try {
            // 判断令牌类型并使用对应的密钥
            String tokenType = getTokenType(token);
            SecretKey key = "refresh".equals(tokenType) ? getRefreshSigningKey() : getAccessSigningKey();
            
            // 获取令牌的过期时间
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            Date expiration = claims.getExpiration();
            long expirationMillis = expiration.getTime() - System.currentTimeMillis();
            
            if (expirationMillis > 0) {
                // 加入黑名单
                tokenRedisService.addToBlacklist(token, expirationMillis);
                
                // 从活跃令牌中删除
                if ("refresh".equals(tokenType)) {
                    tokenRedisService.removeRefreshToken(token);
                } else {
                    tokenRedisService.removeAccessToken(token);
                }
                
                log.info("令牌已失效: {}", token.substring(0, 20) + "...");
            }
        } catch (Exception e) {
            log.error("使令牌失效失败", e);
        }
    }

    // 获取访问令牌剩余过期时间
    public long getAccessTokenExpiration(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getAccessSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            Date expiration = claims.getExpiration();
            return expiration.getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    // 获取刷新令牌剩余过期时间
    public long getRefreshTokenExpiration(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getRefreshSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            
            Date expiration = claims.getExpiration();
            return expiration.getTime() - System.currentTimeMillis();
        } catch (Exception e) {
            return 0;
        }
    }
}
