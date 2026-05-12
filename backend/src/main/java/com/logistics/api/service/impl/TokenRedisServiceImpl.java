package com.logistics.api.service.impl;

import com.logistics.api.service.TokenRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TokenRedisServiceImpl implements TokenRedisService {

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    private static final String ACCESS_TOKEN_PREFIX = "access_token:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String BLACKLIST_PREFIX = "blacklist:";
    private static final String USER_BLACKLIST_PREFIX = "user_blacklist:";
    private static final String BLACKLISTED_USERS_SET = "system:blacklisted_users";

    private boolean isRedisAvailable() {
        return redisTemplate != null;
    }

    @Override
    public void storeAccessToken(String token, String username, long expiration) {
        if (!isRedisAvailable()) {
            log.debug("Redis不可用，跳过存储访问令牌");
            return;
        }
        log.debug("存储访问令牌, username: {}, expiration: {}", username, expiration);
        String key = ACCESS_TOKEN_PREFIX + token;
        redisTemplate.opsForValue().set(key, username, expiration, TimeUnit.MILLISECONDS);
    }

    @Override
    public void storeRefreshToken(String token, String username, long expiration) {
        if (!isRedisAvailable()) {
            log.debug("Redis不可用，跳过存储刷新令牌");
            return;
        }
        log.debug("存储刷新令牌, username: {}, expiration: {}", username, expiration);
        String key = REFRESH_TOKEN_PREFIX + token;
        redisTemplate.opsForValue().set(key, username, expiration, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean validateAccessToken(String token) {
        if (!isRedisAvailable()) {
            log.debug("Redis不可用，默认返回true");
            return true;
        }
        String key = ACCESS_TOKEN_PREFIX + token;
        Boolean hasKey = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(hasKey) && !isBlacklisted(token);
    }

    @Override
    public boolean validateRefreshToken(String token) {
        if (!isRedisAvailable()) {
            log.debug("Redis不可用，默认返回true");
            return true;
        }
        String key = REFRESH_TOKEN_PREFIX + token;
        Boolean hasKey = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(hasKey) && !isBlacklisted(token);
    }

    @Override
    public void addToBlacklist(String token, long expiration) {
        if (!isRedisAvailable()) {
            log.debug("Redis不可用，跳过添加黑名单");
            return;
        }
        log.debug("将令牌加入黑名单");
        String key = BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "1", expiration, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (!isRedisAvailable()) {
            return false;
        }
        String key = BLACKLIST_PREFIX + token;
        Boolean hasKey = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(hasKey);
    }

    @Override
    public void removeAccessToken(String token) {
        if (!isRedisAvailable()) {
            return;
        }
        log.debug("删除访问令牌");
        String key = ACCESS_TOKEN_PREFIX + token;
        redisTemplate.delete(key);
    }

    @Override
    public void removeRefreshToken(String token) {
        if (!isRedisAvailable()) {
            return;
        }
        log.debug("删除刷新令牌");
        String key = REFRESH_TOKEN_PREFIX + token;
        redisTemplate.delete(key);
    }

    @Override
    public void blacklistUser(String username, String reason) {
        if (!isRedisAvailable()) {
            log.debug("Redis不可用，跳过拉黑操作");
            return;
        }
        
        log.info("将用户加入黑名单: username={}, reason={}", username, reason);
        
        String blacklistKey = USER_BLACKLIST_PREFIX + username;
        long blacklistTime = System.currentTimeMillis();
        String blacklistInfo = String.format("{\"reason\":\"%s\",\"blacklistTime\":%d,\"operator\":\"admin\"}",
                reason != null ? reason : "违规操作", blacklistTime);
        
        redisTemplate.opsForValue().set(blacklistKey, blacklistInfo);
        redisTemplate.opsForSet().add(BLACKLISTED_USERS_SET, username);
        
        revokeUserTokens(username);
        
        log.info("用户已成功拉黑: username={}", username);
    }

    /**
     * 撤销用户的所有令牌（内部方法，仅用于拉黑用户时调用）
     */
    private void revokeUserTokens(String username) {
        if (!isRedisAvailable()) {
            return;
        }
        
        try {
            Set<String> accessKeys = redisTemplate.keys(ACCESS_TOKEN_PREFIX + "*");
            if (accessKeys != null && !accessKeys.isEmpty()) {
                for (String key : accessKeys) {
                    String value = redisTemplate.opsForValue().get(key);
                    if (username.equals(value)) {
                        String token = key.substring(ACCESS_TOKEN_PREFIX.length());
                        
                        Long expiration = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
                        if (expiration != null && expiration > 0) {
                            addToBlacklist(token, expiration);
                        }
                        
                        redisTemplate.delete(key);
                        log.debug("已失效访问令牌: token={}...", token.substring(0, Math.min(20, token.length())));
                    }
                }
            }
            
            Set<String> refreshKeys = redisTemplate.keys(REFRESH_TOKEN_PREFIX + "*");
            if (refreshKeys != null && !refreshKeys.isEmpty()) {
                for (String key : refreshKeys) {
                    String value = redisTemplate.opsForValue().get(key);
                    if (username.equals(value)) {
                        String token = key.substring(REFRESH_TOKEN_PREFIX.length());
                        
                        Long expiration = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
                        if (expiration != null && expiration > 0) {
                            addToBlacklist(token, expiration);
                        }
                        
                        redisTemplate.delete(key);
                        log.debug("已失效刷新令牌: token={}...", token.substring(0, Math.min(20, token.length())));
                    }
                }
            }
            
            log.info("撤销用户令牌完成: username={}", username);
        } catch (Exception e) {
            log.error("撤销用户令牌失败: username={}", username, e);
        }
    }

    @Override
    public void removeFromBlacklist(String username) {
        if (!isRedisAvailable()) {
            log.debug("Redis不可用，跳过解封操作");
            return;
        }
        
        log.info("将用户从黑名单中移除: username={}", username);
        
        String blacklistKey = USER_BLACKLIST_PREFIX + username;
        redisTemplate.delete(blacklistKey);
        redisTemplate.opsForSet().remove(BLACKLISTED_USERS_SET, username);
        
        log.info("用户已成功解封: username={}", username);
    }

    @Override
    public boolean isUserBlacklisted(String username) {
        if (!isRedisAvailable()) {
            return false;
        }
        String key = USER_BLACKLIST_PREFIX + username;
        Boolean hasKey = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(hasKey);
    }

    @Override
    public String getBlacklistInfo(String username) {
        if (!isRedisAvailable()) {
            return null;
        }
        String key = USER_BLACKLIST_PREFIX + username;
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public Set<String> getBlacklistedUsers() {
        if (!isRedisAvailable()) {
            return Set.of();
        }
        return redisTemplate.opsForSet().members(BLACKLISTED_USERS_SET);
    }

    @Override
    public Long getTokenExpiration(String token) {
        if (!isRedisAvailable()) {
            return null;
        }
        String key = ACCESS_TOKEN_PREFIX + token;
        return redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
    }
}
