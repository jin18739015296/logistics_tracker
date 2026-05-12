package com.logistics.api.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * MQ 消息统一格式
 * 所有MQ消息都应使用此格式
 * 使用 JSON 序列化，不需要实现 Serializable
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MQMessage<T> {

    /**
     * 消息唯一ID
     */
    private String messageId;

    /**
     * 消息类型（使用 MQConstants.MSG_TYPE_* 常量）
     */
    private String messageType;

    /**
     * 消息版本号
     */
    private String version;

    /**
     * 消息优先级（1-10）
     */
    private Integer priority;

    /**
     * 消息发送时间
     */
    private LocalDateTime timestamp;

    /**
     * 消息发送者
     */
    private String sender;

    /**
     * 业务数据
     */
    private T payload;

    /**
     * 扩展属性
     */
    private Map<String, Object> extra;

    /**
     * 创建消息构建器
     */
    public static <T> MQMessageBuilder<T> builder() {
        return new MQMessageBuilder<T>();
    }

    /**
     * 消息构建器
     */
    public static class MQMessageBuilder<T> {
        private String messageId;
        private String messageType;
        private String version = MQConstants.MESSAGE_VERSION;
        private Integer priority = MQConstants.PRIORITY_NORMAL;
        private LocalDateTime timestamp = LocalDateTime.now();
        private String sender = "system";
        private T payload;
        private Map<String, Object> extra = new HashMap<>();

        public MQMessageBuilder<T> messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public MQMessageBuilder<T> messageType(String messageType) {
            this.messageType = messageType;
            return this;
        }

        public MQMessageBuilder<T> version(String version) {
            this.version = version;
            return this;
        }

        public MQMessageBuilder<T> priority(Integer priority) {
            this.priority = priority;
            return this;
        }

        public MQMessageBuilder<T> timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public MQMessageBuilder<T> sender(String sender) {
            this.sender = sender;
            return this;
        }

        public MQMessageBuilder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        public MQMessageBuilder<T> extra(Map<String, Object> extra) {
            this.extra = extra;
            return this;
        }

        public MQMessageBuilder<T> addExtra(String key, Object value) {
            this.extra.put(key, value);
            return this;
        }

        public MQMessage<T> build() {
            MQMessage<T> message = new MQMessage<>();
            message.messageId = this.messageId != null ? this.messageId : generateMessageId();
            message.messageType = this.messageType;
            message.version = this.version;
            message.priority = this.priority;
            message.timestamp = this.timestamp;
            message.sender = this.sender;
            message.payload = this.payload;
            message.extra = this.extra;
            return message;
        }

        private String generateMessageId() {
            return java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }

    /**
     * 获取路由键
     */
    public String getRoutingKey() {
        if (extra != null && extra.containsKey("routingKey")) {
            return extra.get("routingKey").toString();
        }
        return "";
    }

    /**
     * 设置路由键
     */
    public void setRoutingKey(String routingKey) {
        if (extra == null) {
            extra = new HashMap<>();
        }
        extra.put("routingKey", routingKey);
    }
}
