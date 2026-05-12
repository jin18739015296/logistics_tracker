package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.model.User;
import com.logistics.api.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<Result<User>> getCurrentUser(Principal principal) {
        String username = principal.getName();
        User user = userService.getUserByUsername(username);
        if (user == null) {
            return ResponseEntity.status(404).body(Result.error(20001, "用户不存在"));
        }
        user.setPassword(null);
        return ResponseEntity.ok(Result.success(user));
    }

    @PutMapping("/me")
    public ResponseEntity<Result<User>> updateCurrentUser(@RequestBody Map<String, String> updates, Principal principal) {
        try {
            String username = principal.getName();
            User user = userService.updateUser(username, updates);
            if (user == null) {
                return ResponseEntity.status(404).body(Result.error(20001, "用户不存在"));
            }
            user.setPassword(null);
            return ResponseEntity.ok(Result.success("更新成功", user));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(20002, e.getMessage()));
        }
    }

    @PutMapping("/me/password")
    public ResponseEntity<Result<String>> changePassword(@RequestBody Map<String, String> request, Principal principal) {
        try {
            String username = principal.getName();
            String oldPassword = request.get("oldPassword");
            String newPassword = request.get("newPassword");
            
            userService.changePassword(username, oldPassword, newPassword);
            return ResponseEntity.ok(Result.success("密码修改成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(20003, e.getMessage()));
        }
    }

    @GetMapping("/me/stats")
    public ResponseEntity<Result<Map<String, Object>>> getUserStats(Principal principal) {
        String username = principal.getName();
        Map<String, Object> stats = userService.getUserStats(username);
        return ResponseEntity.ok(Result.success(stats));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<List<User>>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        users.forEach(user -> user.setPassword(null));
        return ResponseEntity.ok(Result.success(users));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<User>> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        if (user == null) {
            return ResponseEntity.status(404).body(Result.error(20001, "用户不存在"));
        }
        user.setPassword(null);
        return ResponseEntity.ok(Result.success(user));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Result<String>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(Result.success("用户删除成功"));
    }

    @GetMapping("/by-phone/{phone}")
    public ResponseEntity<Result<User>> getUserByPhone(@PathVariable String phone) {
        User user = userService.getUserByPhone(phone);
        if (user == null) {
            return ResponseEntity.status(404).body(Result.error(20001, "用户不存在"));
        }
        user.setPassword(null);
        return ResponseEntity.ok(Result.success(user));
    }

    @PutMapping("/me/avatar")
    public ResponseEntity<Result<User>> updateAvatar(@RequestBody Map<String, String> request, Principal principal) {
        try {
            String username = principal.getName();
            String avatarUrl = request.get("avatar");
            if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Result.error(20004, "头像URL不能为空"));
            }
            User user = userService.updateAvatar(username, avatarUrl);
            if (user == null) {
                return ResponseEntity.status(404).body(Result.error(20001, "用户不存在"));
            }
            user.setPassword(null);
            return ResponseEntity.ok(Result.success("头像更新成功", user));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Result.error(20005, e.getMessage()));
        }
    }
}
