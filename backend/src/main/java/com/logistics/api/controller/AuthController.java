package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.model.User;
import com.logistics.api.service.AuthService;
import com.logistics.api.service.CourierStatusService;
import com.logistics.api.service.UserService;
import com.logistics.api.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserService userService;

    @Autowired
    private CourierStatusService courierStatusService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<Result<User>> register(@RequestBody User user) {
        try {
            User registeredUser = authService.register(user);
            registeredUser.setPassword(null);
            return ResponseEntity.ok(Result.success("注册成功", registeredUser));
        } catch (RuntimeException e) {
            log.warn("注册失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Result.error(10001, e.getMessage()));
        }
    }

    @PostMapping("/register/courier")
    public ResponseEntity<Result<Map<String, Object>>> registerCourier(
            @RequestBody Map<String, Object> request) {
        try {
            log.info("配送员注册申请, request keys: {}", request.keySet());
            
            // 解析用户信息
            User user = new User();
            user.setUsername((String) request.get("username"));
            user.setPassword((String) request.get("password"));
            user.setName((String) request.get("name"));
            user.setPhone((String) request.get("phone"));
            user.setEmail((String) request.get("email"));
            
            // 解析配送员申请信息 - 包含所有字段
            Map<String, String> applicationInfo = new HashMap<>();
            
            // 身份认证信息
            applicationInfo.put("idCardNo", (String) request.get("idCardNo"));
            applicationInfo.put("idCardFront", (String) request.get("idCardFront"));
            applicationInfo.put("idCardBack", (String) request.get("idCardBack"));
            applicationInfo.put("idCardHold", (String) request.get("idCardHold"));
            
            // 车辆信息
            applicationInfo.put("vehicleType", (String) request.get("vehicleType"));
            applicationInfo.put("vehiclePlate", (String) request.get("vehiclePlate"));
            applicationInfo.put("vehiclePhoto", (String) request.get("vehiclePhoto"));
            
            // 驾驶证信息
            applicationInfo.put("driverLicenseNo", (String) request.get("driverLicenseNo"));
            applicationInfo.put("driverLicensePhoto", (String) request.get("driverLicensePhoto"));
            
            // 行驶证信息
            applicationInfo.put("vehicleLicenseNo", (String) request.get("vehicleLicenseNo"));
            applicationInfo.put("vehicleLicensePhoto", (String) request.get("vehicleLicensePhoto"));
            
            // 紧急联系人信息
            applicationInfo.put("emergencyContactName", (String) request.get("emergencyContactName"));
            applicationInfo.put("emergencyContactPhone", (String) request.get("emergencyContactPhone"));
            applicationInfo.put("emergencyContactRelation", (String) request.get("emergencyContactRelation"));
            
            // 工作相关
            applicationInfo.put("workCity", (String) request.get("workCity"));
            applicationInfo.put("workDistrict", (String) request.get("workDistrict"));
            
            // 备注
            applicationInfo.put("remark", (String) request.get("remark"));
            
            log.info("applicationInfo: {}", applicationInfo);
            
            User registeredUser = authService.registerCourier(user, applicationInfo);
            registeredUser.setPassword(null);
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("user", registeredUser);
            result.put("message", "配送员注册申请已提交，请等待管理员审核");
            
            return ResponseEntity.ok(Result.success("申请提交成功", result));
        } catch (RuntimeException e) {
            log.warn("配送员注册失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Result.error(10007, e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Result<Map<String, String>>> login(@RequestBody Map<String, String> credentials) {
        try {
            String username = credentials.get("username");
            String password = credentials.get("password");

            Map<String, String> tokens = authService.login(username, password);
            return ResponseEntity.ok(Result.success("登录成功", tokens));
        } catch (RuntimeException e) {
            log.warn("登录失败: {}", e.getMessage());
            return ResponseEntity.status(401).body(Result.error(10002, e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Result<String>> logout(HttpServletRequest request, @RequestBody(required = false) Map<String, String> body) {
        try {
            String accessToken = extractTokenFromRequest(request);
            String refreshToken = body != null ? body.get("refreshToken") : null;

            // 1. 如果是配送员，先获取用户信息并执行下线
            if (accessToken != null) {
                try {
                    String username = jwtUtils.getUsernameFromToken(accessToken);
                    String role = jwtUtils.getRoleFromToken(accessToken);
                    
                    // 如果是配送员，自动下线
                    if ("delivery".equals(role)) {
                        User user = userService.getUserByUsername(username);
                        if (user != null) {
                            log.info("配送员退出登录，自动下线: courierId={}, username={}", user.getId(), username);
                            courierStatusService.goOffline(user.getId());
                        }
                    }
                } catch (Exception e) {
                    log.warn("获取用户信息失败，继续执行登出: {}", e.getMessage());
                }
            }

            // 2. 执行登出操作（使token失效）
            authService.logout(accessToken, refreshToken);

            return ResponseEntity.ok(Result.success("登出成功"));
        } catch (RuntimeException e) {
            log.warn("登出失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Result.error(10003, e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Result<Map<String, String>>> refreshToken(@RequestBody Map<String, String> request) {
        try {
            String refreshToken = request.get("refreshToken");
            Map<String, String> tokens = authService.refreshToken(refreshToken);
            return ResponseEntity.ok(Result.success("令牌刷新成功", tokens));
        } catch (RuntimeException e) {
            log.warn("刷新令牌失败: {}", e.getMessage());
            return ResponseEntity.status(401).body(Result.error(10004, e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Result<String>> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String newPassword = request.get("newPassword");
            authService.resetPassword(username, newPassword);

            return ResponseEntity.ok(Result.success("密码重置成功，请重新登录"));
        } catch (RuntimeException e) {
            log.warn("重置密码失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Result.error(10005, e.getMessage()));
        }
    }



    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
