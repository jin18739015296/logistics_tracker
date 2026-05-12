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
import org.springframework.stereotype.Component;

/**
 * 订单支付成功消息消费者
 * 接收 MQMessage 格式的消息并处理
 */
@Slf4j
@Component
public class OrderPaidConsumer {

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private GrabOrderService grabOrderService;

    @Autowired
    private ReliableMessageService reliableMessageService;

    /**
     * 消费订单支付成功消息
     * 消息格式: MQMessage<Long> (payload 为 orderId)
     */
    @RabbitListener(queues = "${spring.rabbitmq.queues.payment-success:payment_success_queue}", containerFactory = "rabbitListenerContainerFactory")
    public void consume(MQMessage<Long> message) {
        if (message == null || message.getPayload() == null) {
            log.warn("收到无效的支付成功消息");
            return;
        }

        Long orderId = message.getPayload();
        log.info("消费订单支付成功消息, orderId: {}, messageType: {}", 
                orderId, message.getMessageType());

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
