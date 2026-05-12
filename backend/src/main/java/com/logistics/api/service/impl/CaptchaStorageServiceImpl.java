package com.logistics.api.service.impl;

import com.logistics.api.service.CaptchaStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CaptchaStorageServiceImpl implements CaptchaStorageService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    // 内存存储作为降级方案
    private final Map<String, CaptchaEntry> memoryStorage = new ConcurrentHashMap<>();

    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final long CAPTCHA_EXPIRATION = 5; // 5分钟

    @Override
    public void store(String key, String code) {
        log.debug("存储验证码, key: {}", key);
        String redisKey = CAPTCHA_PREFIX + key;

        try {
            // 尝试使用Redis
            redisTemplate.opsForValue().set(redisKey, code, CAPTCHA_EXPIRATION, TimeUnit.MINUTES);
        } catch (Exception e) {
            // Redis不可用，降级为内存存储
            log.warn("Redis不可用，降级为内存存储验证码");
            memoryStorage.put(key, new CaptchaEntry(code, System.currentTimeMillis()));
        }
    }

    @Override
    public String get(String key) {
        log.debug("获取验证码, key: {}", key);
        String redisKey = CAPTCHA_PREFIX + key;

        try {
            // 尝试从Redis获取
            String code = redisTemplate.opsForValue().get(redisKey);
            if (code != null) {
                return code;
            }
        } catch (Exception e) {
            log.warn("Redis不可用，从内存获取验证码");
        }

        // 从内存存储获取
        CaptchaEntry entry = memoryStorage.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.code;
        }

        // 清理过期条目
        if (entry != null && entry.isExpired()) {
            memoryStorage.remove(key);
        }

        return null;
    }

    @Override
    public void delete(String key) {
        log.debug("删除验证码, key: {}", key);
        String redisKey = CAPTCHA_PREFIX + key;

        try {
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("Redis删除失败");
        }

        memoryStorage.remove(key);
    }

    @Override
    public void cleanExpired() {
        log.debug("清理过期验证码");
        long now = System.currentTimeMillis();
        memoryStorage.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    // 内存存储条目
    private static class CaptchaEntry {
        final String code;
        final long timestamp;
        static final long EXPIRATION_MS = 5 * 60 * 1000; // 5分钟

        CaptchaEntry(String code, long timestamp) {
            this.code = code;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        boolean isExpired(long now) {
            return now - timestamp > EXPIRATION_MS;
        }
    }
}
