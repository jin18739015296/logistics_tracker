package com.logistics.api.service;

import java.util.Set;

/**
 * Token Redis 服务接口
 * 用于管理 JWT 令牌的存储、验证和黑名单
 */
public interface TokenRedisService {

    /**
     * 存储访问令牌
     * @param token 令牌
     * @param username 用户名
     * @param expiration 过期时间（毫秒）
     */
    void storeAccessToken(String token, String username, long expiration);

    /**
     * 存储刷新令牌
     * @param token 令牌
     * @param username 用户名
     * @param expiration 过期时间（毫秒）
     */
    void storeRefreshToken(String token, String username, long expiration);

    /**
     * 验证访问令牌是否存在且有效
     * @param token 令牌
     * @return 是否有效
     */
    boolean validateAccessToken(String token);

    /**
     * 验证刷新令牌是否存在且有效
     * @param token 令牌
     * @return 是否有效
     */
    boolean validateRefreshToken(String token);

    /**
     * 将令牌加入黑名单
     * @param token 令牌
     * @param expiration 过期时间（毫秒）
     */
    void addToBlacklist(String token, long expiration);

    /**
     * 检查令牌是否在黑名单中
     * @param token 令牌
     * @return 是否在黑名单
     */
    boolean isBlacklisted(String token);

    /**
     * 删除访问令牌
     * @param token 令牌
     */
    void removeAccessToken(String token);

    /**
     * 删除刷新令牌
     * @param token 令牌
     */
    void removeRefreshToken(String token);

    /**
     * 获取令牌的剩余过期时间
     * @param token 令牌
     * @return 剩余时间（毫秒）
     */
    Long getTokenExpiration(String token);

    /**
     * 将用户加入黑名单（拉黑用户）
     * @param username 用户名
     * @param reason 拉黑原因
     */
    void blacklistUser(String username, String reason);

    /**
     * 将用户从黑名单中移除（解封用户）
     * @param username 用户名
     */
    void removeFromBlacklist(String username);

    /**
     * 检查用户是否在黑名单中
     * @param username 用户名
     * @return 是否在黑名单
     */
    boolean isUserBlacklisted(String username);

    /**
     * 获取用户黑名单信息
     * @param username 用户名
     * @return 黑名单信息（包含原因、时间等），如果不在黑名单返回null
     */
    String getBlacklistInfo(String username);

    /**
     * 获取所有被拉黑的用户列表
     * @return 被拉黑的用户名列表
     */
    Set<String> getBlacklistedUsers();
}
