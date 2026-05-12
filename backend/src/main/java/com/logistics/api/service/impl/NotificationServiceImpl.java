package com.logistics.api.service.impl;

import com.logistics.api.common.BusinessException;
import com.logistics.api.common.ErrorCode;
import com.logistics.api.mapper.NotificationMapper;
import com.logistics.api.model.Notification;
import com.logistics.api.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Override
    public void sendNotification(Long userId, String userType, String type, String title, String content) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setUserType(userType);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setIsRead(0);

        notificationMapper.insert(notification);
        log.info("发送通知成功, userId: {}, userType: {}, title: {}", userId, userType, title);
    }

    @Override
    public void sendOrderNotification(Long userId, String userType, String title, String content, Long orderId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setUserType(userType);
        notification.setType("order");
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRelatedId(orderId);
        notification.setRelatedType("order");
        notification.setIsRead(0);

        notificationMapper.insert(notification);
        log.info("发送订单通知成功, userId: {}, orderId: {}, title: {}", userId, orderId, title);
    }

    @Override
    public void sendComplaintResultNotification(Long userId, String userType, String title, String content,
                                                Long orderId, Long complaintId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setUserType(userType);
        notification.setType("complaint");
        notification.setTitle(title);
        notification.setContent(content);
        if (complaintId != null) {
            notification.setRelatedId(complaintId);
            notification.setRelatedType("complaint");
        } else if (orderId != null) {
            notification.setRelatedId(orderId);
            notification.setRelatedType("order");
        }
        notification.setIsRead(0);
        notificationMapper.insert(notification);
        log.info("发送投诉结果通知, userId: {}, userType: {}, complaintId: {}, orderId: {}",
                userId, userType, complaintId, orderId);
    }

    @Override
    public List<Notification> getUserNotifications(Long userId, String userType, int page, int size) {
        int offset = (page - 1) * size;
        return notificationMapper.selectByUser(userId, userType, size, offset);
    }

    @Override
    public int getUnreadCount(Long userId, String userType) {
        return notificationMapper.countUnread(userId, userType);
    }

    @Override
    public void markAsRead(Long notificationId, Long userId, String userType) {
        int n = notificationMapper.markAsReadForOwner(notificationId, userId, userType);
        if (n == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在或无权操作");
        }
        log.info("标记消息已读, notificationId: {}, userId: {}, userType: {}", notificationId, userId, userType);
    }

    @Override
    public void markAllAsRead(Long userId, String userType) {
        notificationMapper.markAllAsRead(userId, userType);
        log.info("标记所有消息已读, userId: {}, userType: {}", userId, userType);
    }

    @Override
    public void deleteNotification(Long notificationId, Long userId, String userType) {
        int n = notificationMapper.deleteByIdForOwner(notificationId, userId, userType);
        if (n == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消息不存在或无权操作");
        }
        log.info("删除消息, notificationId: {}, userId: {}, userType: {}", notificationId, userId, userType);
    }
}
