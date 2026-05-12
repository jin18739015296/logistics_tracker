package com.logistics.api.service;

import com.logistics.api.model.User;

import java.util.Map;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户注册
     */
    User register(User user);

    /**
     * 配送员注册（需要审核）
     * @param user 用户信息
     * @param applicationInfo 配送员申请信息（真实姓名、身份证号、车辆信息等）
     * @return 注册成功的用户
     */
    User registerCourier(User user, Map<String, String> applicationInfo);

    /**
     * 用户登录
     */
    Map<String, String> login(String username, String password);

    /**
     * 用户登出
     */
    void logout(String accessToken, String refreshToken);

    /**
     * 刷新令牌
     */
    Map<String, String> refreshToken(String refreshToken);



    /**
     * 重置密码
     */
    void resetPassword(String username, String newPassword);
}
