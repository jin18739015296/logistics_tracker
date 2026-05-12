package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 本地消息记录表
 * 用于可靠消息投递
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageRecord {
    
    /**
     * 消息状态：待发送
     */
    public static final int STATUS_PENDING = 0;
    
    /**
     * 消息状态：发送成功
     */
    public static final int STATUS_SENT = 1;
    
    /**
     * 消息状态：发送失败
     */
    public static final int STATUS_FAILED = 2;
    
    /**
     * 消息状态：消费成功
     */
    public static final int STATUS_CONSUMED = 3;
    
    private Long id;
    
    /**
     * 消息类型：payment_success/order_created 等
     */
    private String messageType;
    
    /**
     * 业务ID（如订单ID）
     */
    private Long businessId;
    
    /**
     * RabbitMQ交换机
     */
    private String exchange;
    
    /**
     * RabbitMQ路由键
     */
    private String routingKey;
    
    /**
     * RabbitMQ队列名
     */
    private String queueName;
    
    /**
     * 消息内容（JSON格式）
     */
    private String payload;
    
    /**
     * 状态：0待发送 1发送成功 2发送失败 3消费成功
     */
    private Integer status;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 错误信息
     */
    private String errorMsg;
    
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
