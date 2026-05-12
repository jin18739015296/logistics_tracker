package com.logistics.api.service;

/**
 * 验证码存储服务接口
 * 优先使用 Redis，Redis 不可用时自动降级为内存存储
 */
public interface CaptchaStorageService {

    /**
     * 存储验证码
     * @param key  键（通常是手机号）
     * @param code 验证码
     */
    void store(String key, String code);

    /**
     * 获取验证码
     * @param key 键
     * @return 验证码
     */
    String get(String key);

    /**
     * 删除验证码
     * @param key 键
     */
    void delete(String key);

    /**
     * 清理过期的内存验证码（可以定时调用）
     */
    void cleanExpired();
}
