package com.logistics.api.service;

import com.logistics.api.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface UserService {

    User getUserByUsername(String username);

    User getUserByPhone(String phone);

    User getUserById(Long id);

    List<User> getAllUsers();

    User updateUser(String username, Map<String, String> updates);

    void changePassword(String username, String oldPassword, String newPassword);

    void deleteUser(Long id);

    Map<String, Object> getUserStats(String username);

    Page<User> getUsersWithFilter(String keyword, String role, String status, Pageable pageable);

    void updateUserStatus(Long id, String status, String reason);

    void updateUserRole(Long id, String role);

    Map<String, Object> getUserStatistics();

    List<User> getCouriersWithFilter(String status, String keyword);

    long countOnlineCouriers();

    long countActiveUsersToday();

    void adjustUserBalance(Long userId, BigDecimal amount, String type, String remark);

    List<User> getCouriersByStatus(String status);

    Map<String, Object> getCourierReviewDetail(Long courierId);

    void approveCourier(Long courierId, String remark);

    void rejectCourier(Long courierId, String reason);

    Map<String, Object> getCourierComplaints(Long courierId, int page, int size);
    
    /**
     * 初始化配送员状态
     */
    void initCourierStatus(Long courierId);
    
    /**
     * 更新用户头像
     */
    User updateAvatar(String username, String avatarUrl);
}
