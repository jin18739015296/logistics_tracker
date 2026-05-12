package com.logistics.api.messaging;

import com.logistics.api.config.RabbitMQProperties;
import com.logistics.api.model.User;
import com.logistics.api.service.DispatchService;
import com.logistics.api.service.GrabOrderService;
import com.logistics.api.service.ReliableMessageService;
import com.logistics.api.service.impl.ReliableMessageServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 旧版消费者（裸 Long 载荷）。当前生产发送的是 {@link MQMessage}，与 {@link OrderPaidConsumer} 共用
 * {@code payment_success_queue} 会导致消息被错误消费。默认关闭，仅在迁移遗留消息时按需开启。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "spring.rabbitmq", name = "legacy-payment-consumer-enabled", havingValue = "true")
public class PaymentSuccessConsumer {

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private GrabOrderService grabOrderService;

    @Autowired
    private RabbitMQProperties rabbitMQProperties;

    @Autowired
    private ReliableMessageService reliableMessageService;

    /**
     * 旧版消费者 - 接收直接的 Long 类型消息
     * 已弃用，请使用 OrderPaidConsumer 处理 MQMessage 格式消息
     * 保留此消费者以兼容旧消息格式
     */
    @RabbitListener(queues = "#{rabbitMQProperties.getPaymentSuccess()}", containerFactory = "rabbitListenerContainerFactory")
    public void consumeLegacy(Long orderId) {
        log.info("消费支付成功消息, orderId: {}", orderId);

        try {
            User assignedCourier = dispatchService.autoAssignCourier(orderId);

            if (assignedCourier != null) {
                log.info("支付成功，配送员自动分配完成, orderId: {}, courierId: {}", 
                    orderId, assignedCourier.getId());
            } else {
                log.info("暂无可用配送员，订单加入抢单池, orderId: {}", orderId);
                grabOrderService.addToGrabPool(orderId);
            }

            reliableMessageService.markAsConsumed(orderId, ReliableMessageServiceImpl.MESSAGE_TYPE_PAYMENT_SUCCESS);

        } catch (Exception e) {
            log.error("配送员自动分配失败, orderId: {}", orderId, e);
            try {
                grabOrderService.addToGrabPool(orderId);
                log.info("订单已加入抢单池等待配送员抢单, orderId: {}", orderId);
            } catch (Exception ex) {
                log.error("加入抢单池失败, orderId: {}", orderId, ex);
            }
            reliableMessageService.markAsConsumed(orderId, ReliableMessageServiceImpl.MESSAGE_TYPE_PAYMENT_SUCCESS);
        }
    }
}
