package com.logistics.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 * 声明消息队列和交换机，配置 JSON 序列化/反序列化
 */
@Configuration
public class RabbitMQConfig {

    @Autowired
    private RabbitMQProperties rabbitMQProperties;

    /**
     * 配置 JSON 消息转换器
     * 用于消息的序列化和反序列化
     * 支持 JDK 8 日期时间类型 (LocalDateTime)
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        // 注册 JavaTimeModule 以支持 JDK 8 日期时间类型
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * 配置 RabbitTemplate
     * 使用 JSON 消息转换器
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    /**
     * 配置 RabbitListenerContainerFactory
     * 使用 JSON 消息转换器，用于消费者反序列化
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        // 设置并发消费者数量
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }

    // ============================================
    // Exchange 声明
    // ============================================

    /**
     * 订单相关 Exchange
     */
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange("logistics.order", true, false);
    }

    /**
     * 配送相关 Exchange
     */
    @Bean
    public TopicExchange deliveryExchange() {
        return new TopicExchange("logistics.delivery", true, false);
    }

    /**
     * 通知相关 Exchange
     */
    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange("logistics.notification", true, false);
    }

    /**
     * 物流轨迹 Exchange
     */
    @Bean
    public TopicExchange trackingExchange() {
        return new TopicExchange("logistics.tracking", true, false);
    }

    /**
     * 数据同步 Exchange
     */
    @Bean
    public TopicExchange dataSyncExchange() {
        return new TopicExchange("logistics.data.sync", true, false);
    }

    // ============================================
    // Queue 声明
    // ============================================

    /**
     * 轨迹上传队列
     * 用于低频轨迹的异步写入
     */
    @Bean
    public Queue trackQueue() {
        return new Queue(rabbitMQProperties.getTrack(), true);
    }

    /**
     * 支付成功队列
     * 用于支付完成后异步分配配送员
     */
    @Bean
    public Queue paymentSuccessQueue() {
        return new Queue(rabbitMQProperties.getPaymentSuccess(), true);
    }

    /**
     * 订单分配队列
     * 用于订单分配任务
     */
    @Bean
    public Queue orderDispatchQueue() {
        return new Queue(rabbitMQProperties.getOrderDispatch(), true);
    }

    /**
     * 物流事件队列
     * 用于物流节点事件处理
     */
    @Bean
    public Queue logisticsEventQueue() {
        return new Queue(rabbitMQProperties.getLogisticsEvent(), true);
    }

    /**
     * 配送员状态同步队列
     * 用于配送员状态/位置变更异步同步到数据库
     */
    @Bean
    public Queue courierStatusSyncQueue() {
        return new Queue(rabbitMQProperties.getCourierStatusSync(), true);
    }

    // ============================================
    // Binding 声明 - 队列与交换机绑定
    // ============================================

    /**
     * 支付成功队列绑定到订单交换机
     */
    @Bean
    public Binding paymentSuccessBinding() {
        return BindingBuilder.bind(paymentSuccessQueue())
                .to(orderExchange())
                .with("order.paid.*");
    }

    /**
     * 订单分配队列绑定到配送交换机
     */
    @Bean
    public Binding orderDispatchBinding() {
        return BindingBuilder.bind(orderDispatchQueue())
                .to(deliveryExchange())
                .with("delivery.order.dispatch.*");
    }

    /**
     * 轨迹队列绑定到轨迹交换机
     */
    @Bean
    public Binding trackBinding() {
        return BindingBuilder.bind(trackQueue())
                .to(trackingExchange())
                .with("tracking.upload.*");
    }

    /**
     * 物流事件队列绑定到订单交换机
     */
    @Bean
    public Binding logisticsEventBinding() {
        return BindingBuilder.bind(logisticsEventQueue())
                .to(orderExchange())
                .with("order.event.*");
    }
}
