package com.logistics.api.controller.admin;

import com.logistics.api.common.Result;
import com.logistics.api.model.CourierApplication;
import com.logistics.api.model.User;
import com.logistics.api.mapper.CourierApplicationMapper;
import com.logistics.api.mapper.UserMapper;
import com.logistics.api.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/courier-applications")
@PreAuthorize("hasAnyRole('ADMIN', 'admin')")
public class AdminCourierApplicationController {

    @Autowired
    private CourierApplicationMapper courierApplicationMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private UserService userService;



    /**
     * 获取所有申请列表（支持筛选）
     */
    @GetMapping("/list")
    public ResponseEntity<Result<Map<String, Object>>> getApplicationList(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String phone) {
        log.info("管理员获取配送员申请列表: status={}, phone={}", status, phone);
        
        List<CourierApplication> applications = courierApplicationMapper.selectAllWithUser(status, phone);
        
        Map<String, Object> result = new HashMap<>();
        result.put("applications", applications);
        result.put("total", applications.size());
        
        return ResponseEntity.ok(Result.success(result));
    }



    /**
     * 审核通过
     */
    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<Result<String>> approveApplication(
            @PathVariable Long applicationId,
            @RequestBody(required = false) Map<String, String> request) {
        log.info("管理员审核通过配送员申请: applicationId={}", applicationId);
        
        try {
            CourierApplication application = courierApplicationMapper.selectById(applicationId);
            if (application == null) {
                return ResponseEntity.status(404).body(Result.error(40401, "申请记录不存在"));
            }
            
            if (!"pending".equals(application.getStatus())) {
                return ResponseEntity.badRequest().body(Result.error(40001, "该申请已处理，无法重复审核"));
            }
            
            // 获取当前管理员ID
            Long reviewerId = getCurrentAdminId();
            
            // 更新申请状态
            courierApplicationMapper.updateStatus(applicationId, "approved", reviewerId, null);
            
            // 更新用户角色为配送员，状态为正常
            User user = userMapper.selectById(application.getUserId());
            if (user != null) {
                user.setRole("delivery");
                user.setStatus(1); // 1-正常
                userMapper.updateById(user);
                
                // 初始化配送员状态
                userService.initCourierStatus(user.getId());
            }
            
            log.info("配送员申请审核通过: applicationId={}, userId={}", applicationId, application.getUserId());
            return ResponseEntity.ok(Result.success("审核通过成功"));
        } catch (Exception e) {
            log.error("审核通过失败: applicationId={}", applicationId, e);
            return ResponseEntity.badRequest().body(Result.error(50001, "审核失败: " + e.getMessage()));
        }
    }

    /**
     * 审核拒绝
     */
    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<Result<String>> rejectApplication(
            @PathVariable Long applicationId,
            @RequestBody Map<String, String> request) {
        log.info("管理员审核拒绝配送员申请: applicationId={}", applicationId);
        
        try {
            String reason = request != null ? request.get("reason") : null;
            
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Result.error(40001, "请填写拒绝原因"));
            }
            
            CourierApplication application = courierApplicationMapper.selectById(applicationId);
            if (application == null) {
                return ResponseEntity.status(404).body(Result.error(40401, "申请记录不存在"));
            }
            
            if (!"pending".equals(application.getStatus())) {
                return ResponseEntity.badRequest().body(Result.error(40001, "该申请已处理，无法重复审核"));
            }
            
            // 获取当前管理员ID
            Long reviewerId = getCurrentAdminId();
            
            // 更新申请状态
            courierApplicationMapper.updateStatus(applicationId, "rejected", reviewerId, reason);
            
            // 更新用户状态为正常（拒绝后恢复为正常状态）
            User user = userMapper.selectById(application.getUserId());
            if (user != null) {
                user.setStatus(1); // 1-正常
                userMapper.updateById(user);
            }
            
            log.info("配送员申请审核拒绝: applicationId={}, reason={}", applicationId, reason);
            return ResponseEntity.ok(Result.success("审核拒绝成功"));
        } catch (Exception e) {
            log.error("审核拒绝失败: applicationId={}", applicationId, e);
            return ResponseEntity.badRequest().body(Result.error(50001, "审核失败: " + e.getMessage()));
        }
    }



    /**
     * 获取当前管理员ID
     */
    private Long getCurrentAdminId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getId();
        }
        return null;
    }
}
