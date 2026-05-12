package com.logistics.api.service.impl;

import com.logistics.api.model.CourierApplication;
import com.logistics.api.model.User;
import com.logistics.api.mapper.CourierApplicationMapper;
import com.logistics.api.mapper.UserMapper;
import com.logistics.api.service.AuthService;
import com.logistics.api.service.TokenRedisService;
import com.logistics.api.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final CourierApplicationMapper courierApplicationMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    @Autowired
    private TokenRedisService tokenRedisService;

    public AuthServiceImpl(UserMapper userMapper, CourierApplicationMapper courierApplicationMapper, 
                          PasswordEncoder passwordEncoder, JwtUtils jwtUtils) {
        this.userMapper = userMapper;
        this.courierApplicationMapper = courierApplicationMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public User register(User user) {
        log.info("用户注册: username={}, role={}", user.getUsername(), user.getRole());
        
        // 检查用户名是否已存在
        if (userMapper.existsByUsername(user.getUsername())) {
            log.warn("注册失败: 用户名已存在 - {}", user.getUsername());
            throw new RuntimeException("Username already exists");
        }

        // 检查手机号是否已被同角色用户使用
        // 普通用户和配送员分开计算，一个手机号可以分别注册一个普通用户和一个配送员账号
        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            if (userMapper.existsByPhoneAndRole(user.getPhone(), user.getRole())) {
                String roleName = "delivery".equals(user.getRole()) ? "配送员" : "普通用户";
                log.warn("注册失败: 手机号已注册{}账号 - phone={}, role={}", roleName, user.getPhone(), user.getRole());
                throw new RuntimeException("该手机号已注册" + roleName + "账号，请使用其他手机号");
            }
        }

        // 密码加密
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 设置默认状态为正常（如果未设置）
        if (user.getStatus() == null) {
            user.setStatus(1); // 1-正常
        }

        // 保存用户
        userMapper.insert(user);
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    @Override
    @Transactional
    public User registerCourier(User user, Map<String, String> applicationInfo) {
        log.info("配送员注册申请: username={}, phone={}", user.getUsername(), user.getPhone());

        // 1. 注册为配送员角色，但状态为审核中（status=2）
        user.setRole("delivery");
        user.setStatus(2); // 2-审核中
        User registeredUser = register(user);
        
        // 2. 创建配送员审核申请（适配新的表结构）
        CourierApplication application = new CourierApplication();
        application.setUserId(registeredUser.getId());
        application.setStatus("pending");  // 待审核状态
        
        // 身份认证信息
        application.setIdCardNo(applicationInfo.get("idCardNo"));
        application.setIdCardFront(applicationInfo.get("idCardFront"));
        application.setIdCardBack(applicationInfo.get("idCardBack"));
        application.setIdCardHold(applicationInfo.get("idCardHold"));
        
        // 车辆信息
        application.setVehicleType(applicationInfo.get("vehicleType"));
        application.setVehiclePlate(applicationInfo.get("vehiclePlate"));
        application.setVehiclePhoto(applicationInfo.get("vehiclePhoto"));
        
        // 驾驶证信息（汽车/摩托车必填）
        application.setDriverLicenseNo(applicationInfo.get("driverLicenseNo"));
        application.setDriverLicensePhoto(applicationInfo.get("driverLicensePhoto"));
        
        // 行驶证信息
        application.setVehicleLicenseNo(applicationInfo.get("vehicleLicenseNo"));
        application.setVehicleLicensePhoto(applicationInfo.get("vehicleLicensePhoto"));
        
        // 紧急联系人信息
        application.setEmergencyContactName(applicationInfo.get("emergencyContactName"));
        application.setEmergencyContactPhone(applicationInfo.get("emergencyContactPhone"));
        application.setEmergencyContactRelation(applicationInfo.get("emergencyContactRelation"));

        // 工作相关
        application.setWorkCity(applicationInfo.get("workCity"));
        application.setWorkDistrict(applicationInfo.get("workDistrict"));

        // 备注
        application.setRemark(applicationInfo.get("remark"));
        
        courierApplicationMapper.insert(application);
        log.info("配送员申请已保存到数据库: applicationId={}", application.getId());
        
        log.info("配送员注册申请已提交: userId={}, applicationId={}, 等待管理员审核", 
                registeredUser.getId(), application.getId());
        
        return registeredUser;
    }

    @Override
    public Map<String, String> login(String username, String password) {
        log.info("用户登录: username={}", username);
        
        // 查找用户
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            log.warn("登录失败: 用户不存在 - {}", username);
            throw new RuntimeException("用户名或密码错误");
        }

        // 验证密码
        boolean passwordMatch = passwordEncoder.matches(password, user.getPassword());
        
        if (!passwordMatch) {
            log.warn("登录失败: 密码错误 - username={}", username);
            throw new RuntimeException("用户名或密码错误");
        }

        // 检查用户是否被拉黑
        if (tokenRedisService.isUserBlacklisted(username)) {
            String blacklistInfo = tokenRedisService.getBlacklistInfo(username);
            log.warn("登录失败: 用户已被拉黑 - username={}, blacklistInfo={}", username, blacklistInfo);
            throw new RuntimeException("账号已被封禁，如有疑问请联系管理员");
        }

        // 检查用户状态
        Integer userStatus = user.getStatus();
        if (userStatus == 0) {
            log.warn("登录失败: 用户已被禁用 - username={}", username);
            throw new RuntimeException("账号已被禁用，如有疑问请联系管理员");
        }
        if (userStatus == 2) {
            log.warn("登录失败: 配送员申请待审核 - username={}", username);
            throw new RuntimeException("您的配送员申请正在审核中，请耐心等待管理员审批");
        }

        // 检查配送员申请是否被拒绝
        if ("delivery".equals(user.getRole())) {
            CourierApplication application = courierApplicationMapper.selectByUserId(user.getId());
            if (application != null && "rejected".equals(application.getStatus())) {
                log.warn("登录失败: 配送员申请被拒绝 - username={}", username);
                throw new RuntimeException("您的配送员申请未通过审核，原因：" +
                        (application.getRejectReason() != null ? application.getRejectReason() : "不符合要求"));
            }
        }

        try {
            // 生成双令牌
            String accessToken = jwtUtils.generateAccessToken(user.getUsername(), user.getRole());
            String refreshToken = jwtUtils.generateRefreshToken(user.getUsername());

            // 返回令牌和用户信息
            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", accessToken);
            tokens.put("refreshToken", refreshToken);
            tokens.put("userId", String.valueOf(user.getId()));
            tokens.put("role", user.getRole());
            tokens.put("username", user.getUsername());
            tokens.put("name", user.getName());
            tokens.put("avatar", user.getAvatar());
            
            log.info("用户登录成功: userId={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole());
            return tokens;
        } catch (Exception e) {
            log.error("登录失败: Token生成异常 - username={}", username, e);
            throw new RuntimeException("登录失败，请稍后重试");
        }
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        log.info("用户登出");
        
        // 使访问令牌失效
        if (accessToken != null && !accessToken.isEmpty()) {
            jwtUtils.invalidateToken(accessToken);
        }
        
        // 使刷新令牌失效
        if (refreshToken != null && !refreshToken.isEmpty()) {
            jwtUtils.invalidateToken(refreshToken);
        }
        
        log.info("用户登出成功");
    }

    @Override
    public Map<String, String> refreshToken(String refreshToken) {
        try {
            // 验证刷新令牌
            if (!jwtUtils.validateRefreshToken(refreshToken)) {
                log.warn("刷新令牌失败: 令牌无效或已过期");
                throw new RuntimeException("登录已过期，请重新登录");
            }

            // 从刷新令牌中获取用户名
            String username = jwtUtils.getUsernameFromRefreshToken(refreshToken);
            log.info("刷新令牌: username={}", username);

            // 查找用户
            User user = userMapper.selectByUsername(username);
            if (user == null) {
                log.warn("刷新令牌失败: 用户不存在 - {}", username);
                throw new RuntimeException("用户不存在");
            }

            // 检查用户是否被拉黑
            if (tokenRedisService.isUserBlacklisted(username)) {
                log.warn("刷新令牌失败: 用户已被拉黑 - username={}", username);
                throw new RuntimeException("账号已被封禁，如有疑问请联系管理员");
            }

            // 检查用户状态
            Integer userStatus = user.getStatus();
            if (userStatus == 0) {
                log.warn("刷新令牌失败: 用户已被禁用 - username={}", username);
                throw new RuntimeException("账号已被禁用，如有疑问请联系管理员");
            }

            // 使旧令牌失效
            jwtUtils.invalidateToken(refreshToken);

            // 生成新的双令牌
            String newAccessToken = jwtUtils.generateAccessToken(user.getUsername(), user.getRole());
            String newRefreshToken = jwtUtils.generateRefreshToken(user.getUsername());

            // 返回新令牌
            Map<String, String> tokens = new HashMap<>();
            tokens.put("accessToken", newAccessToken);
            tokens.put("refreshToken", newRefreshToken);

            log.info("刷新令牌成功: username={}", username);
            return tokens;
        } catch (RuntimeException e) {
            log.warn("刷新令牌失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("刷新令牌失败", e);
            throw new RuntimeException("登录已过期，请重新登录");
        }
    }



    @Override
    public void resetPassword(String username, String newPassword) {
        log.info("重置密码: username={}", username);
        
        // 查找用户
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            log.warn("重置密码失败: 用户不存在 - {}", username);
            throw new RuntimeException("用户不存在");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        
        log.info("重置密码成功: userId={}, username={}", user.getId(), user.getUsername());
    }
}
