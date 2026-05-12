package com.logistics.api.service;

import com.logistics.api.model.MessageRecord;

/**
 * 可靠消息服务接口
 * 本地消息表 + 定时任务补偿机制
 */
public interface ReliableMessageService {

    /**
     * 发送消息（先发送，后落库）
     * 
     * @param messageType 消息类型
     * @param businessId  业务ID
     * @param payload     消息内容
     * @param exchange    交换机
     * @param routingKey  路由键
     * @param queueName   队列名
     * @return 消息记录ID
     */
    Long sendMessage(String messageType, Long businessId, Object payload,
                     String exchange, String routingKey, String queueName);

    /**
     * 发送支付成功消息
     * 
     * @param orderId 订单ID
     * @return 消息记录ID
     */
    Long sendPaymentSuccessMessage(Long orderId);

    /**
     * 发送订单创建消息
     * 
     * @param orderId 订单ID
     * @return 消息记录ID
     */
    Long sendOrderCreatedMessage(Long orderId);

    /**
     * 标记消息为消费成功
     * 
     * @param businessId  业务ID
     * @param messageType 消息类型
     */
    void markAsConsumed(Long businessId, String messageType);
}
