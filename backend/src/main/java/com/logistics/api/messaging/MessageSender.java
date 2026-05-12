package com.logistics.api.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.api.model.MessageRecord;
import com.logistics.api.model.OrderTrack;
import com.logistics.api.service.MessageRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 通用消息发送组件
 * 基于本地消息表模式实现可靠消息发送
 * 
 * 使用方式：
 * 1. 先保存消息到本地表（状态：待发送）
 * 2. 发送消息到 MQ
 * 3. 更新消息状态（状态：已发送）
 * 4. 定时任务补偿失败的消息
 */
@Slf4j
@Component
public class MessageSender {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MessageRecordService messageRecordService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 发送消息（带本地消息表保障）
     * 
     * @param messageType 消息类型
     * @param businessId 业务ID
     * @param payload 消息内容（已经是MQMessage格式）
     * @param exchange 交换机
     * @param routingKey 路由键
     * @param queueName 队列名（使用默认交换机时）
     * @return 消息记录ID
     */
    @Transactional
    public Long send(String messageType, Long businessId, Object payload,
                     String exchange, String routingKey, String queueName) {
        log.info("发送消息, messageType: {}, businessId: {}", messageType, businessId);

        // 1. 将消息内容序列化为JSON
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("消息序列化失败, messageType: {}, businessId: {}", messageType, businessId, e);
            throw new RuntimeException("消息序列化失败", e);
        }

        // 2. 先保存消息到本地表（状态：待发送）
        MessageRecord record = new MessageRecord();
        record.setMessageType(messageType);
        record.setBusinessId(businessId);
        record.setExchange(exchange);
        record.setRoutingKey(routingKey);
        record.setQueueName(queueName);
        record.setPayload(payloadJson);
        record.setStatus(MessageRecord.STATUS_PENDING);
        record.setRetryCount(0);
        record.setCreateTime(LocalDateTime.now());

        messageRecordService.save(record);
        Long messageId = record.getId();

        // 3. 发送消息到 MQ（payload已经是MQMessage格式，直接发送）
        boolean sendSuccess = false;
        String errorMsg = null;
        try {
            if (exchange != null && !exchange.isEmpty()) {
                rabbitTemplate.convertAndSend(exchange, routingKey, payload);
            } else {
                rabbitTemplate.convertAndSend("", queueName, payload);
            }
            sendSuccess = true;
            log.info("消息发送成功, messageId: {}, messageType: {}, businessId: {}", 
                    messageId, messageType, businessId);
        } catch (Exception e) {
            log.error("消息发送失败, messageId: {}, messageType: {}, businessId: {}", 
                    messageId, messageType, businessId, e);
            errorMsg = e.getMessage();
        }

        // 4. 更新消息状态
        if (sendSuccess) {
            messageRecordService.markAsSent(messageId);
        } else {
            messageRecordService.markAsFailed(messageId, errorMsg);
        }

        return messageId;
    }

    /**
     * 发送消息（简化版，使用默认交换机）
     */
    @Transactional
    public Long send(String messageType, Long businessId, Object payload, String queueName) {
        return send(messageType, businessId, payload, "", "", queueName);
    }

    /**
     * 发送订单支付成功消息
     */
    @Transactional
    public Long sendOrderPaidMessage(Long orderId, String queueName) {
        MQMessage<Long> message = MQMessage.<Long>builder()
                .messageType(MQConstants.MSG_TYPE_ORDER_PAID)
                .payload(orderId)
                .addExtra("orderId", orderId)
                .build();
        
        return send("ORDER_PAID", orderId, message, queueName);
    }

    /**
     * 发送配送员状态变更消息
     */
    @Transactional
    public Long sendCourierStatusMessage(CourierStatusSyncMessage syncMessage, String queueName) {
        String messageType = syncMessage.getMessageType();
        MQMessage<CourierStatusSyncMessage> message = MQMessage.<CourierStatusSyncMessage>builder()
                .messageType(MQConstants.MSG_TYPE_COURIER_STATUS)
                .payload(syncMessage)
                .addExtra("courierId", syncMessage.getCourierId())
                .addExtra("eventType", messageType)
                .build();
        
        return send("COURIER_STATUS", syncMessage.getCourierId(), message, queueName);
    }

    /**
     * 发送轨迹上传消息
     */
    @Transactional
    public Long sendTrackMessage(OrderTrack track, String queueName) {
        MQMessage<OrderTrack> message = MQMessage.<OrderTrack>builder()
                .messageType(MQConstants.MSG_TYPE_TRACK_UPLOAD)
                .payload(track)
                .addExtra("orderId", track.getOrderId())
                .addExtra("courierId", track.getCourierId())
                .build();
        
        return send("TRACK_UPLOAD", track.getOrderId(), message, queueName);
    }
}
