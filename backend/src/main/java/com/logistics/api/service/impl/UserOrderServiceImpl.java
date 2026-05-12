package com.logistics.api.service.impl;

import com.logistics.api.common.BusinessException;
import com.logistics.api.common.ErrorCode;
import com.logistics.api.enums.OrderStatus;
import com.logistics.api.mapper.CourierReviewMapper;
import com.logistics.api.mapper.DeliveryTaskMapper;
import com.logistics.api.mapper.LogisticsEventMapper;
import com.logistics.api.mapper.OrderAddressMapper;
import com.logistics.api.mapper.OrderMapper;
import com.logistics.api.mapper.UserMapper;
import com.logistics.api.model.CourierReview;
import com.logistics.api.model.DeliveryTask;
import com.logistics.api.model.LogisticsEvent;
import com.logistics.api.model.Order;
import com.logistics.api.model.OrderAddress;
import com.logistics.api.model.User;
import com.logistics.api.service.NotificationService;
import com.logistics.api.service.UserOrderService;
import com.logistics.api.service.WalletService;
import com.logistics.api.service.support.NotificationCopy;
import com.logistics.api.service.support.OrderStatusValidator;
import com.logistics.api.service.support.OrderViewerPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户订单操作服务实现
 */
@Slf4j
@Service
public class UserOrderServiceImpl implements UserOrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderAddressMapper orderAddressMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DeliveryTaskMapper deliveryTaskMapper;

    @Autowired
    private LogisticsEventMapper logisticsEventMapper;

    @Autowired
    private CourierReviewMapper courierReviewMapper;

    @Autowired
    private OrderStatusValidator orderStatusValidator;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private WalletService walletService;

    @Override
    @Transactional
    public void cancelOrder(Long orderId, Long userId, String reason) {
        log.info("用户取消订单, orderId: {}, userId: {}, reason: {}", orderId, userId, reason);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        if (!userId.equals(order.getUserId())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_OWNER, "无权操作此订单");
        }

        if (!OrderStatus.canCancel(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_CANNOT_CANCEL,
                    "订单当前状态为 " + OrderStatus.getDescByCode(order.getStatus()) + "，不允许取消");
        }

        orderStatusValidator.requireTransition(order.getStatus(), OrderStatus.CANCELLED.getCode());

        boolean wasPaid = !OrderStatus.PENDING.getCode().equals(order.getStatus());

        orderMapper.updateStatus(orderId, OrderStatus.CANCELLED.getCode());

        DeliveryTask task = deliveryTaskMapper.selectByOrderId(orderId);
        if (task != null) {
            deliveryTaskMapper.updateStatus(task.getId(), OrderStatus.CANCELLED.getCode());
        }

        // 已支付订单取消时自动全额退款到钱包（通过钱包流水表记录，不修改订单表）
        BigDecimal toRefund = BigDecimal.ZERO;
        if (wasPaid) {
            BigDecimal actual = order.getActualAmount() != null ? order.getActualAmount() : BigDecimal.ZERO;
            BigDecimal refunded = walletService.getRefundedAmount(orderId);
            toRefund = actual.subtract(refunded).max(BigDecimal.ZERO);
            if (toRefund.compareTo(BigDecimal.ZERO) > 0) {
                try {
                    walletService.refund(order.getUserId(), toRefund, orderId, order.getOrderNo(),
                            "用户取消订单自动全额退款" + (reason != null && !reason.isEmpty() ? "：" + reason : ""));
                    // 退款成功通知用户
                    notificationService.sendNotification(
                            order.getUserId(),
                            "user",
                            "system",
                            NotificationCopy.refundSuccessTitle(),
                            NotificationCopy.refundSuccessBody(order.getOrderNo(), toRefund)
                    );
                } catch (Exception e) {
                    log.warn("取消订单钱包退款失败, orderId={}, err={}", orderId, e.getMessage());
                    throw new BusinessException(ErrorCode.PAYMENT_ERROR, "退款到钱包失败: " + e.getMessage());
                }
            }
        }

        // 记录取消物流事件（退款属于内部流程，不在物流事件中展示）
        LogisticsEvent event = new LogisticsEvent();
        event.setOrderId(orderId);
        event.setStatus(OrderStatus.CANCELLED.getCode());
        event.setDescription("用户取消订单" + (reason != null && !reason.isEmpty() ? "，原因：" + reason : ""));
        event.setOperatorId(userId);
        event.setCreateTime(LocalDateTime.now());
        logisticsEventMapper.insert(event);

        log.info("订单取消成功, orderId: {}, wasPaid: {}", orderId, wasPaid);
    }

    @Override
    @Transactional
    public void confirmReceived(Long orderId, Long userId) {
        log.info("用户确认收货, orderId: {}, userId: {}", orderId, userId);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        OrderAddress recv = orderAddressMapper.selectByOrderIdAndType(orderId, "receiver");
        if (recv == null || !OrderViewerPolicy.phonesMatch(user.getPhone(), recv.getContactPhone())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅收件人可确认收货");
        }

        String st = order.getStatus();
        if (!OrderStatus.DELIVERED.getCode().equals(st)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR,
                    "仅已送达订单可确认收货，当前：" + OrderStatus.getDescByCode(st));
        }
        orderStatusValidator.requireTransition(st, OrderStatus.COMPLETED.getCode());

        orderMapper.updateStatus(orderId, OrderStatus.COMPLETED.getCode());

        LogisticsEvent event = new LogisticsEvent();
        event.setOrderId(orderId);
        event.setStatus(OrderStatus.COMPLETED.getCode());
        event.setDescription("用户已确认收货，订单完成");
        event.setOperatorId(userId);
        event.setCreateTime(LocalDateTime.now());
        logisticsEventMapper.insert(event);

        log.info("确认收货成功, orderId: {}", orderId);
    }

    @Override
    @Transactional
    public void reviewOrder(Long orderId, Long userId, Integer rating, String content, String tags) {
        log.info("用户评价订单, orderId: {}, userId: {}, rating: {}", orderId, userId, rating);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        OrderAddress recv = orderAddressMapper.selectByOrderIdAndType(orderId, "receiver");
        if (recv == null || !OrderViewerPolicy.phonesMatch(user.getPhone(), recv.getContactPhone())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅收件人可评价本订单");
        }

        if (!OrderStatus.COMPLETED.getCode().equals(order.getStatus())
                && !OrderStatus.DELIVERED.getCode().equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单状态不允许评价");
        }

        if (order.getCourierId() == null) {
            throw new BusinessException(ErrorCode.COURIER_NOT_FOUND, "订单暂无配送员，无法评价");
        }

        CourierReview existingReview = courierReviewMapper.selectByOrderIdAndUserId(orderId, userId);
        if (existingReview != null) {
            throw new BusinessException(ErrorCode.ALREADY_EXISTS, "您已评价过此订单");
        }

        CourierReview review = new CourierReview();
        review.setCourierId(order.getCourierId());
        review.setOrderId(orderId);
        review.setUserId(userId);
        review.setRating(rating);
        review.setContent(content);
        review.setTags(tags);
        review.setCreateTime(LocalDateTime.now());
        courierReviewMapper.insert(review);

        LogisticsEvent event = new LogisticsEvent();
        event.setOrderId(orderId);
        event.setStatus(order.getStatus());
        event.setDescription("用户已评价");
        event.setOperatorId(userId);
        event.setCreateTime(LocalDateTime.now());
        logisticsEventMapper.insert(event);

        try {
            notificationService.sendNotification(
                    order.getCourierId(),
                    "courier",
                    "system",
                    NotificationCopy.userReviewCourierTitle(),
                    NotificationCopy.userReviewCourierBody(
                            order.getOrderNo(),
                            rating,
                            content)
            );
        } catch (Exception e) {
            log.warn("评价后通知配送员失败, orderId: {}, err: {}", orderId, e.getMessage());
        }

        log.info("订单评价成功, orderId: {}", orderId);
    }

    @Override
    public List<LogisticsEvent> getOrderEvents(Long orderId, Long userId) {
        log.debug("用户获取订单物流事件, orderId: {}, userId: {}", orderId, userId);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        boolean isSender = userId.equals(order.getUserId());
        OrderAddress recv = orderAddressMapper.selectByOrderIdAndType(orderId, "receiver");
        boolean isReceiver = OrderViewerPolicy.phonesMatch(user.getPhone(),
                recv != null ? recv.getContactPhone() : null);
        if (!isSender && !isReceiver) {
            throw new BusinessException(ErrorCode.ORDER_NOT_OWNER, "无权查看此订单");
        }
        if (isReceiver) {
            DeliveryTask t = deliveryTaskMapper.selectByOrderId(orderId);
            if (!OrderViewerPolicy.receiverCanViewOrder(order, t)) {
                throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
            }
        }

        List<LogisticsEvent> list = logisticsEventMapper.selectByOrderId(orderId);
        // 用户端时间轴：常规状态不用后端原始 description（避免透出抢单/分配等内部信息）
        // 但取消、异常、退款等关键事件保留 description，让用户了解具体原因
        // 同时，用户端不展示 awaiting_courier_confirm 状态的物流事件
        List<LogisticsEvent> filteredList = list.stream()
                .filter(e -> !OrderStatus.AWAITING_COURIER_CONFIRM.getCode().equals(e.getStatus()))
                .collect(Collectors.toList());
        for (LogisticsEvent e : filteredList) {
            String st = e.getStatus();
            boolean keepDesc = OrderStatus.CANCELLED.getCode().equals(st)
                    || OrderStatus.EXCEPTION.getCode().equals(st);
            if (!keepDesc) {
                e.setDescription(null);
            }
        }
        return filteredList;
    }
}
