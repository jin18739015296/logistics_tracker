package com.logistics.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ 队列配置属性
 * 从 application.yml 中读取队列名称配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "spring.rabbitmq.queues")
public class RabbitMQProperties {

    /**
     * 轨迹上传队列
     */
    private String track = "track_queue";

    /**
     * 支付成功队列
     */
    private String paymentSuccess = "payment_success_queue";

    /**
     * 订单分配队列
     */
    private String orderDispatch = "order_dispatch_queue";

    /**
     * 物流事件队列
     */
    private String logisticsEvent = "logistics_event_queue";

    /**
     * 配送员状态同步队列
     * 用于配送员状态/位置变更异步同步到数据库
     */
    private String courierStatusSync = "courier_status_sync_queue";
}
