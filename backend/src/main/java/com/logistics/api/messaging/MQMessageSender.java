package com.logistics.api.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * MQ 消息发送服务
 * 统一封装消息发送逻辑
 * 使用 JSON 序列化消息
 */
@Slf4j
@Service
public class MQMessageSender {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 发送消息到指定 Exchange
     * 使用 JSON 格式序列化 MQMessage
     *
     * @param exchange   交换机名称
     * @param routingKey 路由键
     * @param message    消息对象
     */
    public void send(String exchange, String routingKey, MQMessage<?> message) {
        try {
            // 将 MQMessage 转换为 JSON 字符串发送
            // 消费者端可以直接接收 MQMessage 对象（由 Jackson2JsonMessageConverter 自动转换）
            rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
                msg.getMessageProperties().setHeader(MQConstants.HEADER_MSG_TYPE, message.getMessageType());
                msg.getMessageProperties().setHeader(MQConstants.HEADER_MSG_VERSION, message.getVersion());
                msg.getMessageProperties().setHeader(MQConstants.HEADER_MSG_PRIORITY, message.getPriority());
                msg.getMessageProperties().setHeader(MQConstants.HEADER_MSG_TIMESTAMP, message.getTimestamp().toString());
                msg.getMessageProperties().setHeader(MQConstants.HEADER_MSG_SENDER, message.getSender());
                // 设置内容类型为 JSON
                msg.getMessageProperties().setContentType("application/json");
                return msg;
            });

            log.debug("消息发送成功, exchange: {}, routingKey: {}, messageType: {}",
                    exchange, routingKey, message.getMessageType());
        } catch (Exception e) {
            log.error("消息发送失败, exchange: {}, routingKey: {}, messageType: {}",
                    exchange, routingKey, message.getMessageType(), e);
            throw new RuntimeException("消息发送失败", e);
        }
    }

    /**
     * 发送订单相关消息
     *
     * @param messageType 消息类型
     * @param payload     业务数据
     * @param orderId     订单ID
     */
    public void sendOrderMessage(String messageType, Object payload, Long orderId) {
        MQMessage<Object> message = MQMessage.builder()
                .messageType(messageType)
                .payload(payload)
                .addExtra("orderId", orderId)
                .build();

        String routingKey = String.format(MQConstants.ROUTING_KEY_ORDER_PATTERN, messageType.toLowerCase(), orderId);
        send(MQConstants.EXCHANGE_ORDER, routingKey, message);
    }

    /**
     * 发送配送相关消息
     *
     * @param messageType 消息类型
     * @param payload     业务数据
     * @param courierId   配送员ID
     */
    public void sendDeliveryMessage(String messageType, Object payload, Long courierId) {
        MQMessage<Object> message = MQMessage.builder()
                .messageType(messageType)
                .payload(payload)
                .addExtra("courierId", courierId)
                .build();

        String routingKey = String.format(MQConstants.ROUTING_KEY_DELIVERY_PATTERN, messageType.toLowerCase(), courierId);
        send(MQConstants.EXCHANGE_DELIVERY, routingKey, message);
    }

    /**
     * 发送用户通知
     *
     * @param userId  用户ID
     * @param payload 通知内容
     */
    public void sendUserNotification(Long userId, Object payload) {
        MQMessage<Object> message = MQMessage.builder()
                .messageType(MQConstants.MSG_TYPE_NOTIFY_ORDER_STATUS)
                .payload(payload)
                .addExtra("userId", userId)
                .build();

        String routingKey = String.format(MQConstants.ROUTING_KEY_USER_NOTIFICATION_PATTERN, userId);
        send(MQConstants.EXCHANGE_NOTIFICATION, routingKey, message);
    }

    /**
     * 发送配送员通知
     *
     * @param courierId 配送员ID
     * @param payload   通知内容
     */
    public void sendCourierNotification(Long courierId, Object payload) {
        MQMessage<Object> message = MQMessage.builder()
                .messageType(MQConstants.MSG_TYPE_NOTIFY_NEW_ORDER)
                .payload(payload)
                .addExtra("courierId", courierId)
                .build();

        String routingKey = String.format(MQConstants.ROUTING_KEY_COURIER_NOTIFICATION_PATTERN, courierId);
        send(MQConstants.EXCHANGE_NOTIFICATION, routingKey, message);
    }

    /**
     * 系统派单实时提醒（MQ → 骑手订阅/WebPush）：不写 App「消息中心」表；用户侧不因派单再发一条站内信，避免与支付成功文案叠床架屋。
     *
     * @param orderId   订单ID
     * @param courierId 配送员ID
     */
    public void sendOrderAssignedNotification(Long orderId, Long courierId) {
        MQMessage<Map<String, Object>> message = MQMessage.<Map<String, Object>>builder()
                .messageType(MQConstants.MSG_TYPE_ORDER_ASSIGNED)
                .priority(MQConstants.PRIORITY_HIGH)
                .payload(Map.of(
                        "orderId", orderId,
                        "courierId", courierId,
                        "message", "您有新的指派订单：请在配送员端打开订单详情，核对寄收件信息与物品描述；尽快致电寄件人约定上门时间，按时完成取件与后续派送。如遇特殊情况无法履约，请及早按规定报备或申请改派。"
                ))
                .addExtra("orderId", orderId)
                .addExtra("courierId", courierId)
                .build();

        String routingKey = String.format(MQConstants.ROUTING_KEY_COURIER_NOTIFICATION_PATTERN, courierId);
        send(MQConstants.EXCHANGE_NOTIFICATION, routingKey, message);

        log.info("订单分配通知已发送, orderId: {}, courierId: {}", orderId, courierId);
    }

    /**
     * 发送轨迹上传消息
     *
     * @param payload 轨迹数据
     */
    public void sendTrackUploadMessage(Object payload) {
        MQMessage<Object> message = MQMessage.builder()
                .messageType(MQConstants.MSG_TYPE_TRACK_UPLOAD)
                .priority(MQConstants.PRIORITY_LOW)
                .payload(payload)
                .build();

        send(MQConstants.EXCHANGE_TRACKING, "tracking.upload", message);
    }

    /**
     * 发送订单支付成功消息
     * 触发自动调度分配配送员
     *
     * @param orderId 订单ID
     */
    public void sendOrderPaidMessage(Long orderId) {
        MQMessage<Long> message = MQMessage.<Long>builder()
                .messageType(MQConstants.MSG_TYPE_ORDER_PAID)
                .priority(MQConstants.PRIORITY_HIGH)
                .payload(orderId)
                .addExtra("orderId", orderId)
                .build();

        send(MQConstants.EXCHANGE_ORDER, "order.paid." + orderId, message);

        log.info("订单支付成功消息已发送, orderId: {}", orderId);
    }
}
