package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.model.Notification;
import com.logistics.api.model.User;
import com.logistics.api.service.NotificationService;
import com.logistics.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserService userService;

    /**
     * 获取当前用户的消息列表
     */
    @GetMapping
    public ResponseEntity<Result<List<Notification>>> getNotifications(
            Principal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = userService.getUserByUsername(principal.getName());
        String userType = getUserType(user);
        List<Notification> notifications = notificationService.getUserNotifications(user.getId(), userType, page, size);
        return ResponseEntity.ok(Result.success(notifications));
    }

    /**
     * 获取未读消息数量
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Result<Map<String, Integer>>> getUnreadCount(Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        String userType = getUserType(user);
        int count = notificationService.getUnreadCount(user.getId(), userType);
        Map<String, Integer> result = new HashMap<>();
        result.put("unreadCount", count);
        return ResponseEntity.ok(Result.success(result));
    }

    /**
     * 标记消息为已读
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Result<String>> markAsRead(@PathVariable Long id, Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        String userType = getUserType(user);
        notificationService.markAsRead(id, user.getId(), userType);
        return ResponseEntity.ok(Result.success("标记已读成功"));
    }

    /**
     * 标记所有消息为已读
     */
    @PostMapping("/read-all")
    public ResponseEntity<Result<String>> markAllAsRead(Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        String userType = getUserType(user);
        notificationService.markAllAsRead(user.getId(), userType);
        return ResponseEntity.ok(Result.success("全部标记已读成功"));
    }

    /**
     * 删除消息
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<String>> deleteNotification(@PathVariable Long id, Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        String userType = getUserType(user);
        notificationService.deleteNotification(id, user.getId(), userType);
        return ResponseEntity.ok(Result.success("删除成功"));
    }

    private String getUserType(User user) {
        if ("delivery".equals(user.getRole())) {
            return "courier";
        } else if ("admin".equals(user.getRole())) {
            return "admin";
        } else {
            return "user";
        }
    }
}
