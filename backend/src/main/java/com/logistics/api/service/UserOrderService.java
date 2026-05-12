package com.logistics.api.service;

import java.util.List;
import com.logistics.api.model.LogisticsEvent;

/**
 * 用户订单操作服务
 * 处理用户对订单的状态更新操作
 */
public interface UserOrderService {

    /**
     * 取消订单
     * @param orderId 订单ID
     * @param userId 用户ID
     * @param reason 取消原因
     */
    void cancelOrder(Long orderId, Long userId, String reason);

    /**
     * 确认收货
     * @param orderId 订单ID
     * @param userId 用户ID
     */
    void confirmReceived(Long orderId, Long userId);

    /**
     * 评价订单
     * @param orderId 订单ID
     * @param userId 用户ID
     * @param rating 评分 1-5
     * @param content 评价内容
     * @param tags 评价标签
     */
    void reviewOrder(Long orderId, Long userId, Integer rating, String content, String tags);

    /**
     * 获取订单物流事件
     * @param orderId 订单ID
     * @param userId 用户ID
     * @return 物流事件列表
     */
    List<LogisticsEvent> getOrderEvents(Long orderId, Long userId);
}
