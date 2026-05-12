package com.logistics.api.controller.admin;

import com.logistics.api.common.Result;
import com.logistics.api.model.User;
import com.logistics.api.service.CourierStatusService;
import com.logistics.api.service.TokenRedisService;
import com.logistics.api.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/admin/couriers")
@PreAuthorize("hasAnyRole('ADMIN', 'admin')")
public class AdminCourierController {

    @Autowired
    private UserService userService;

    @Autowired
    private CourierStatusService courierStatusService;

    @Autowired
    private TokenRedisService tokenRedisService;

    @GetMapping("/pending-review")
    public ResponseEntity<Result<Map<String, Object>>> getPendingReviewCouriers() {
        List<User> couriers = userService.getCouriersByStatus("pending_review");
        
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("couriers", couriers);
        result.put("total", couriers.size());
        
        return ResponseEntity.ok(Result.success(result));
    }



    // ==================== 配送员拉黑/解封功能 ====================

    @PostMapping("/{courierId}/blacklist")
    public ResponseEntity<Result<Map<String, Object>>> blacklistCourier(
            @PathVariable Long courierId,
            @RequestBody(required = false) Map<String, String> request) {
        
        try {
            User courier = userService.getUserById(courierId);
            if (courier == null) {
                return ResponseEntity.status(404).body(Result.error(20001, "配送员不存在"));
            }
            
            if (!"delivery".equals(courier.getRole())) {
                return ResponseEntity.badRequest().body(Result.error(20002, "该用户不是配送员"));
            }
            
            String reason = request != null ? request.get("reason") : "违规操作";
            
            log.info("管理员拉黑配送员: courierId={}, username={}, reason={}", 
                    courierId, courier.getUsername(), reason);
            
            // 1. 将配送员加入黑名单
            tokenRedisService.blacklistUser(courier.getUsername(), reason);
            
            // 2. 强制配送员下线
            courierStatusService.goOffline(courierId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("courierId", courierId);
            result.put("username", courier.getUsername());
            result.put("blacklisted", true);
            result.put("reason", reason);
            result.put("message", "配送员已被成功拉黑，该配送员将立即下线且无法重新登录");
            
            log.info("配送员拉黑成功: courierId={}, username={}", courierId, courier.getUsername());
            return ResponseEntity.ok(Result.success(result));
            
        } catch (Exception e) {
            log.error("拉黑配送员失败: courierId={}", courierId, e);
            return ResponseEntity.badRequest().body(Result.error(20003, "拉黑失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{courierId}/blacklist")
    public ResponseEntity<Result<Map<String, Object>>> unblacklistCourier(@PathVariable Long courierId) {
        
        try {
            User courier = userService.getUserById(courierId);
            if (courier == null) {
                return ResponseEntity.status(404).body(Result.error(20001, "配送员不存在"));
            }
            
            log.info("管理员解封配送员: courierId={}, username={}", courierId, courier.getUsername());
            
            tokenRedisService.removeFromBlacklist(courier.getUsername());
            
            Map<String, Object> result = new HashMap<>();
            result.put("courierId", courierId);
            result.put("username", courier.getUsername());
            result.put("blacklisted", false);
            result.put("message", "配送员已成功解封，可以正常登录接单");
            
            log.info("配送员解封成功: courierId={}, username={}", courierId, courier.getUsername());
            return ResponseEntity.ok(Result.success(result));
            
        } catch (Exception e) {
            log.error("解封配送员失败: courierId={}", courierId, e);
            return ResponseEntity.badRequest().body(Result.error(20004, "解封失败: " + e.getMessage()));
        }
    }





    @GetMapping("/blacklist")
    public ResponseEntity<Result<Map<String, Object>>> getBlacklistedCouriers() {
        
        Set<String> blacklistedUsernames = tokenRedisService.getBlacklistedUsers();
        
        List<Map<String, Object>> blacklistedCouriers = blacklistedUsernames.stream()
                .map(username -> {
                    Map<String, Object> courierInfo = new HashMap<>();
                    courierInfo.put("username", username);
                    courierInfo.put("blacklistInfo", tokenRedisService.getBlacklistInfo(username));
                    User user = userService.getUserByUsername(username);
                    if (user != null && "delivery".equals(user.getRole())) {
                        courierInfo.put("courierId", user.getId());
                        courierInfo.put("name", user.getName());
                        courierInfo.put("phone", user.getPhone());
                        // 从配送员申请信息中获取工作城市
                        Map<String, Object> reviewDetail = userService.getCourierReviewDetail(user.getId());
                        if (reviewDetail != null && reviewDetail.containsKey("workCity")) {
                            courierInfo.put("workCity", reviewDetail.get("workCity"));
                        }
                        return courierInfo;
                    }
                    return null;
                })
                .filter(info -> info != null)
                .toList();
        
        Map<String, Object> result = new HashMap<>();
        result.put("content", blacklistedCouriers);
        result.put("total", blacklistedCouriers.size());
        
        return ResponseEntity.ok(Result.success(result));
    }


}
