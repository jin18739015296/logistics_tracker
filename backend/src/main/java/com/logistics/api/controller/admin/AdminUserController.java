package com.logistics.api.controller.admin;

import com.logistics.api.common.Result;
import com.logistics.api.model.User;
import com.logistics.api.service.TokenRedisService;
import com.logistics.api.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'admin')")
public class AdminUserController {

    @Autowired
    private UserService userService;

    @Autowired
    private TokenRedisService tokenRedisService;

    @GetMapping
    public ResponseEntity<Result<Map<String, Object>>> getUserList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<User> userPage = userService.getUsersWithFilter(keyword, role, status, pageable);
        
        userPage.getContent().forEach(user -> user.setPassword(null));
        
        Map<String, Object> result = new HashMap<>();
        result.put("content", userPage.getContent());
        result.put("totalElements", userPage.getTotalElements());
        result.put("totalPages", userPage.getTotalPages());
        result.put("currentPage", page);
        
        return ResponseEntity.ok(Result.success(result));
    }



    @PutMapping("/{id}/status")
    public ResponseEntity<Result<String>> updateUserStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            String status = request.get("status");
            String reason = request.get("reason");
            userService.updateUserStatus(id, status, reason);
            return ResponseEntity.ok(Result.success("用户状态更新成功"));
        } catch (Exception e) {
            log.error("更新用户状态失败", e);
            return ResponseEntity.badRequest().body(Result.error(20002, e.getMessage()));
        }
    }



    @GetMapping("/roles")
    public ResponseEntity<Result<List<String>>> getAvailableRoles() {
        List<String> roles = List.of("user", "courier", "admin");
        return ResponseEntity.ok(Result.success(roles));
    }

    @PostMapping("/{id}/blacklist")
    public ResponseEntity<Result<Map<String, Object>>> blacklistUser(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> request) {
        
        try {
            User user = userService.getUserById(id);
            if (user == null) {
                return ResponseEntity.status(404).body(Result.error(20001, "用户不存在"));
            }
            
            String reason = request != null ? request.get("reason") : "违规操作";
            
            log.info("管理员拉黑用户: userId={}, username={}, reason={}", 
                    id, user.getUsername(), reason);
            
            tokenRedisService.blacklistUser(user.getUsername(), reason);
            
            Map<String, Object> result = new HashMap<>();
            result.put("userId", id);
            result.put("username", user.getUsername());
            result.put("blacklisted", true);
            result.put("reason", reason);
            result.put("message", "用户已被成功拉黑，该用户无法重新登录");
            
            log.info("用户拉黑成功: userId={}, username={}", id, user.getUsername());
            return ResponseEntity.ok(Result.success(result));
            
        } catch (Exception e) {
            log.error("拉黑用户失败: userId={}", id, e);
            return ResponseEntity.badRequest().body(Result.error(20003, "拉黑失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/blacklist")
    public ResponseEntity<Result<Map<String, Object>>> unblacklistUser(@PathVariable Long id) {
        
        try {
            User user = userService.getUserById(id);
            if (user == null) {
                return ResponseEntity.status(404).body(Result.error(20001, "用户不存在"));
            }
            
            log.info("管理员解封用户: userId={}, username={}", id, user.getUsername());
            
            tokenRedisService.removeFromBlacklist(user.getUsername());
            
            Map<String, Object> result = new HashMap<>();
            result.put("userId", id);
            result.put("username", user.getUsername());
            result.put("blacklisted", false);
            result.put("message", "用户已成功解封，可以正常登录使用");
            
            log.info("用户解封成功: userId={}, username={}", id, user.getUsername());
            return ResponseEntity.ok(Result.success(result));
            
        } catch (Exception e) {
            log.error("解封用户失败: userId={}", id, e);
            return ResponseEntity.badRequest().body(Result.error(20004, "解封失败: " + e.getMessage()));
        }
    }

    @GetMapping("/blacklist")
    public ResponseEntity<Result<Map<String, Object>>> getBlacklistedUsers() {
        
        Set<String> blacklistedUsernames = tokenRedisService.getBlacklistedUsers();
        
        List<Map<String, Object>> blacklistedUsers = blacklistedUsernames.stream()
                .map(username -> {
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("username", username);
                    userInfo.put("blacklistInfo", tokenRedisService.getBlacklistInfo(username));
                    User user = userService.getUserByUsername(username);
                    if (user != null) {
                        userInfo.put("userId", user.getId());
                        userInfo.put("name", user.getName());
                        userInfo.put("role", user.getRole());
                        userInfo.put("phone", user.getPhone());
                    }
                    return userInfo;
                })
                .toList();
        
        Map<String, Object> result = new HashMap<>();
        result.put("content", blacklistedUsers);
        result.put("total", blacklistedUsers.size());
        
        return ResponseEntity.ok(Result.success(result));
    }




}
