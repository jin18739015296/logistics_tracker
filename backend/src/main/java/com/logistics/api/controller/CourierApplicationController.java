package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.model.CourierApplication;
import com.logistics.api.model.User;
import com.logistics.api.service.AuthService;
import com.logistics.api.mapper.CourierApplicationMapper;
import com.logistics.api.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/courier-application")
public class CourierApplicationController {

    @Autowired
    private CourierApplicationMapper courierApplicationMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private AuthService authService;

    /**
     * 根据手机号查询审核进度
     */
    @GetMapping("/status")
    public ResponseEntity<Result<Map<String, Object>>> getApplicationStatus(@RequestParam String phone) {
        log.info("查询配送员审核进度: phone={}", phone);
        
        if (phone == null || phone.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Result.error(40001, "手机号不能为空"));
        }
        
        // 查询最新的申请记录
        CourierApplication application = courierApplicationMapper.selectByPhone(phone);
        
        if (application == null) {
            return ResponseEntity.ok(Result.error(40401, "未找到该手机号的申请记录"));
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("applicationId", application.getId());
        result.put("status", application.getStatus());
        result.put("statusText", getStatusText(application.getStatus()));
        result.put("realName", application.getRealName());
        result.put("phone", application.getPhone());
        result.put("idCardNo", maskIdCard(application.getIdCardNo()));
        result.put("vehicleType", application.getVehicleType());
        result.put("vehicleTypeText", getVehicleTypeText(application.getVehicleType()));
        result.put("vehiclePlate", application.getVehiclePlate());
        result.put("workCity", application.getWorkCity());
        result.put("workDistrict", application.getWorkDistrict());
        result.put("emergencyContactName", application.getEmergencyContactName());
        result.put("emergencyContactPhone", maskPhone(application.getEmergencyContactPhone()));
        result.put("emergencyContactRelation", application.getEmergencyContactRelation());
        result.put("createTime", application.getCreateTime());
        result.put("reviewTime", application.getReviewTime());
        result.put("rejectReason", application.getRejectReason());
        
        // 计算等待天数
        if (application.getCreateTime() != null) {
            long days = java.time.Duration.between(
                application.getCreateTime(), 
                java.time.LocalDateTime.now()
            ).toDays();
            result.put("waitingDays", days);
        }
        
        log.info("查询审核进度成功: phone={}, status={}", phone, application.getStatus());
        return ResponseEntity.ok(Result.success(result));
    }
    
    /**
     * 配送员注册申请
     */
    @PostMapping("/register")
    public ResponseEntity<Result<Map<String, Object>>> registerCourier(
            @RequestBody Map<String, Object> request) {
        log.info("配送员注册申请, request keys: {}", request.keySet());
        log.info("工作城市: {}, 工作区域: {}", request.get("workCity"), request.get("workDistrict"));
        log.info("紧急联系人: {}, 电话: {}, 关系: {}", 
                request.get("emergencyContactName"), 
                request.get("emergencyContactPhone"), 
                request.get("emergencyContactRelation"));
        
        try {
            // 构建用户信息
            User user = new User();
            user.setUsername((String) request.get("username"));
            user.setPassword((String) request.get("password"));
            user.setName((String) request.get("name"));
            user.setPhone((String) request.get("phone"));
            user.setEmail((String) request.get("email"));
            
            // 构建申请信息
            Map<String, String> applicationInfo = new HashMap<>();
            applicationInfo.put("idCardNo", (String) request.get("idCardNo"));
            applicationInfo.put("idCardFront", (String) request.get("idCardFront"));
            applicationInfo.put("idCardBack", (String) request.get("idCardBack"));
            applicationInfo.put("idCardHold", (String) request.get("idCardHold"));
            applicationInfo.put("vehicleType", (String) request.get("vehicleType"));
            applicationInfo.put("vehiclePlate", (String) request.get("vehiclePlate"));
            applicationInfo.put("vehiclePhoto", (String) request.get("vehiclePhoto"));
            applicationInfo.put("driverLicenseNo", (String) request.get("driverLicenseNo"));
            applicationInfo.put("driverLicensePhoto", (String) request.get("driverLicensePhoto"));
            applicationInfo.put("vehicleLicenseNo", (String) request.get("vehicleLicenseNo"));
            applicationInfo.put("vehicleLicensePhoto", (String) request.get("vehicleLicensePhoto"));
            applicationInfo.put("emergencyContactName", (String) request.get("emergencyContactName"));
            applicationInfo.put("emergencyContactPhone", (String) request.get("emergencyContactPhone"));
            applicationInfo.put("emergencyContactRelation", (String) request.get("emergencyContactRelation"));
            applicationInfo.put("workCity", (String) request.get("workCity"));
            applicationInfo.put("workDistrict", (String) request.get("workDistrict"));
            applicationInfo.put("remark", (String) request.get("remark"));
            
            log.info("applicationInfo: {}", applicationInfo);
            
            User registeredUser = authService.registerCourier(user, applicationInfo);
            
            Map<String, Object> result = new HashMap<>();
            result.put("userId", registeredUser.getId());
            result.put("username", registeredUser.getUsername());
            result.put("message", "申请提交成功，请等待管理员审核");
            
            return ResponseEntity.ok(Result.success("申请提交成功", result));
        } catch (Exception e) {
            log.error("配送员注册申请失败", e);
            return ResponseEntity.badRequest().body(Result.error(40002, e.getMessage()));
        }
    }
    
    /**
     * 获取状态文本
     */
    private String getStatusText(String status) {
        switch (status) {
            case "pending":
                return "审核中";
            case "approved":
                return "已通过";
            case "rejected":
                return "已拒绝";
            default:
                return "未知";
        }
    }
    
    /**
     * 获取车辆类型文本
     */
    private String getVehicleTypeText(String vehicleType) {
        switch (vehicleType) {
            case "electric_bike":
                return "电动车";
            case "motorcycle":
                return "摩托车";
            case "small_truck":
                return "小货车";
            case "medium_truck":
                return "中型货车";
            case "large_truck":
                return "大型货车";
            default:
                return "其他";
        }
    }
    
    /**
     * 脱敏身份证号
     */
    private String maskIdCard(String idCardNo) {
        if (idCardNo == null || idCardNo.length() != 18) {
            return idCardNo;
        }
        return idCardNo.substring(0, 6) + "********" + idCardNo.substring(14);
    }

    /**
     * 脱敏手机号
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
