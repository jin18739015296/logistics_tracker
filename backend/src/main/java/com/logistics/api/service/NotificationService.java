package com.logistics.api.service;

import com.logistics.api.model.Notification;

import java.util.List;

public interface NotificationService {

    /**
     * 发送消息通知
     */
    void sendNotification(Long userId, String userType, String type, String title, String content);

    /**
     * 发送订单相关通知
     */
    void sendOrderNotification(Long userId, String userType, String title, String content, Long orderId);

    /**
     * 投诉处理结果（管理员处理完成后），关联投诉 ID 便于端上跳转详情。
     * @param orderId 关联订单，可为空；与 complaintId 至少用于文案或回退
     */
    void sendComplaintResultNotification(Long userId, String userType, String title, String content, Long orderId, Long complaintId);

    /**
     * 获取用户消息列表
     */
    List<Notification> getUserNotifications(Long userId, String userType, int page, int size);

    /**
     * 获取用户未读消息数量
     */
    int getUnreadCount(Long userId, String userType);

    /**
     * 标记消息为已读（仅属主）
     */
    void markAsRead(Long notificationId, Long userId, String userType);

    /**
     * 标记用户所有消息为已读
     */
    void markAllAsRead(Long userId, String userType);

    /**
     * 删除消息（仅属主）
     */
    void deleteNotification(Long notificationId, Long userId, String userType);
}
