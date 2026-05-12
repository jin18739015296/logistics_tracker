package com.logistics.api.messaging;

import com.logistics.api.config.RabbitMQProperties;
import com.logistics.api.mapper.OrderTrackMapper;
import com.logistics.api.model.OrderTrack;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 轨迹消息消费者
 * 按照技术方案 5.5 实现：MQ异步写入数据库
 * 支持接收 MQMessage 格式的消息
 */
@Slf4j
@Component
public class TrackMessageConsumer {

    @Autowired
    private OrderTrackMapper orderTrackMapper;

    @Autowired
    private RabbitMQProperties rabbitMQProperties;

    /**
     * 消费轨迹消息并写入数据库
     * 对应技术方案：5.5 轨迹处理（RabbitMQ）
     * 消息格式: MQMessage<OrderTrack>
     */
    @RabbitListener(queues = "#{rabbitMQProperties.getTrack()}", containerFactory = "rabbitListenerContainerFactory")
    public void consume(MQMessage<OrderTrack> mqMessage) {
        if (mqMessage == null || mqMessage.getPayload() == null) {
            log.warn("收到无效的轨迹消息");
            return;
        }
        
        OrderTrack track = mqMessage.getPayload();

        try {
            log.debug("消费轨迹消息, orderId: {}, lat: {}, lng: {}",
                    track.getOrderId(), track.getLatitude(), track.getLongitude());

            // 写入数据库
            orderTrackMapper.insert(track);

            log.debug("轨迹写入数据库成功, orderId: {}", track.getOrderId());
        } catch (Exception e) {
            log.error("轨迹写入数据库失败, orderId: {}", track.getOrderId(), e);
            // 这里可以添加重试逻辑或死信队列处理
        }
    }
}
