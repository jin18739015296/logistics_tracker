package com.logistics.api.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.api.config.RabbitMQProperties;
import com.logistics.api.mapper.MessageRecordMapper;
import com.logistics.api.messaging.MessageSender;
import com.logistics.api.model.MessageRecord;
import com.logistics.api.service.ReliableMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 可靠消息服务实现
 * 策略：先发送消息，成功后落库
 * 定时任务补偿失败的消息
 */
@Slf4j
@Service
public class ReliableMessageServiceImpl implements ReliableMessageService {

    @Autowired
    private MessageSender messageSender;

    @Autowired
    private MessageRecordMapper messageRecordMapper;

    @Autowired
    private RabbitMQProperties rabbitMQProperties;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 消息类型常量
     */
    public static final String MESSAGE_TYPE_PAYMENT_SUCCESS = "payment_success";
    public static final String MESSAGE_TYPE_ORDER_CREATED = "order_created";

    @Override
    @Transactional
    public Long sendMessage(String messageType, Long businessId, Object payload,
                            String exchange, String routingKey, String queueName) {
        log.info("发送消息, messageType: {}, businessId: {}", messageType, businessId);

        // 1. 先将消息转换为JSON
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("消息序列化失败, messageType: {}, businessId: {}", messageType, businessId, e);
            throw new RuntimeException("消息序列化失败", e);
        }

        // 2. 先尝试发送消息到MQ
        boolean sendSuccess = false;
        String errorMsg = null;
        try {
            // 使用 MessageSender 组件发送消息（带本地消息表保障）
            messageSender.send(messageType, businessId, payload, exchange, routingKey, queueName);
            sendSuccess = true;
            log.info("消息发送成功, messageType: {}, businessId: {}", messageType, businessId);
        } catch (Exception e) {
            log.error("消息发送失败, messageType: {}, businessId: {}", messageType, businessId, e);
            errorMsg = e.getMessage();
        }

        // 3. 记录消息到本地表
        MessageRecord record = new MessageRecord();
        record.setMessageType(messageType);
        record.setBusinessId(businessId);
        record.setExchange(exchange);
        record.setRoutingKey(routingKey);
        record.setQueueName(queueName);
        record.setPayload(payloadJson);
        record.setStatus(sendSuccess ? MessageRecord.STATUS_SENT : MessageRecord.STATUS_FAILED);
        record.setRetryCount(sendSuccess ? 0 : 1);
        record.setErrorMsg(errorMsg);

        messageRecordMapper.insert(record);

        if (!sendSuccess) {
            log.warn("消息已落库，等待定时任务补偿, messageId: {}", record.getId());
        }

        return record.getId();
    }

    @Override
    public Long sendPaymentSuccessMessage(Long orderId) {
        log.info("发送支付成功消息, orderId: {}", orderId);

        return sendMessage(
                MESSAGE_TYPE_PAYMENT_SUCCESS,
                orderId,
                orderId,
                "",  // 使用默认交换机
                "",
                rabbitMQProperties.getPaymentSuccess()
        );
    }

    @Override
    public Long sendOrderCreatedMessage(Long orderId) {
        log.info("发送订单创建消息, orderId: {}", orderId);

        return sendMessage(
                MESSAGE_TYPE_ORDER_CREATED,
                orderId,
                orderId,
                "",  // 使用默认交换机
                "",
                rabbitMQProperties.getOrderDispatch()
        );
    }

    @Override
    public void markAsConsumed(Long businessId, String messageType) {
        log.info("标记消息为消费成功, businessId: {}, messageType: {}", businessId, messageType);
        messageRecordMapper.markAsConsumed(businessId, messageType);
    }
}
