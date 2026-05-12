package com.logistics.api.messaging;

import com.logistics.api.mapper.CourierStatusMapper;
import com.logistics.api.model.CourierStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 配送员状态同步消费者
 * 异步处理配送员状态/位置变更，同步到MySQL数据库
 */
@Slf4j
@Component
public class CourierStatusSyncConsumer {

    @Autowired
    private CourierStatusMapper courierStatusMapper;

    /**
     * 消费配送员状态同步消息
     * 消息格式: MQMessage<CourierStatusSyncMessage>
     */
    @RabbitListener(queues = "${spring.rabbitmq.queues.courier-status-sync:courier_status_sync_queue}", containerFactory = "rabbitListenerContainerFactory")
    public void handleCourierStatusSync(MQMessage<CourierStatusSyncMessage> mqMessage) {
        if (mqMessage == null || mqMessage.getPayload() == null) {
            log.warn("收到无效的配送员状态同步消息");
            return;
        }
        
        CourierStatusSyncMessage message = mqMessage.getPayload();
        
        if (message.getCourierId() == null) {
            log.warn("收到无效的配送员状态同步消息: courierId为空");
            return;
        }

        log.info("收到配送员状态同步消息, courierId: {}, type: {}",
                message.getCourierId(), message.getMessageType());

        try {
            String msgType = message.getMessageType();

            // 仅更新坐标：periodic updateLocation 发送的消息不带 status/orderCount，
            // 若走全字段 update 会把 status、current_order_count 写成 NULL。
            if ("UPDATE_LOCATION".equals(msgType)) {
                if (message.getLatitude() == null || message.getLongitude() == null) {
                    log.warn("UPDATE_LOCATION 缺少经纬度, courierId={}", message.getCourierId());
                    return;
                }
                courierStatusMapper.updateLocation(message.getCourierId(),
                        message.getLatitude(), message.getLongitude());
                log.info("仅更新配送员位置到数据库, courierId: {}", message.getCourierId());
                return;
            }

            // 下线：只改状态与单数，保留最后坐标
            if ("OFFLINE".equals(msgType)) {
                String st = message.getStatus() != null ? message.getStatus() : "offline";
                int oc = message.getCurrentOrderCount() != null ? message.getCurrentOrderCount() : 0;
                courierStatusMapper.updateOfflineState(message.getCourierId(), st, oc);
                log.info("配送员下线状态落库（保留坐标）, courierId: {}", message.getCourierId());
                return;
            }

            CourierStatus courierStatus = new CourierStatus();
            courierStatus.setCourierId(message.getCourierId());

            if (message.getStatus() != null) {
                courierStatus.setStatus(message.getStatus());
            }

            if (message.getLatitude() != null) {
                courierStatus.setCurrentLat(BigDecimal.valueOf(message.getLatitude()));
            }
            if (message.getLongitude() != null) {
                courierStatus.setCurrentLng(BigDecimal.valueOf(message.getLongitude()));
            }

            if (message.getCurrentOrderCount() != null) {
                courierStatus.setCurrentOrderCount(message.getCurrentOrderCount());
            }

            courierStatus.setUpdateTime(LocalDateTime.now());

            if (Boolean.TRUE.equals(message.getIsNewRecord())) {
                if (courierStatus.getCurrentOrderCount() == null) {
                    courierStatus.setCurrentOrderCount(0);
                }
                courierStatusMapper.insert(courierStatus);
                log.info("插入配送员状态到数据库, courierId: {}", message.getCourierId());
            } else {
                courierStatusMapper.update(courierStatus);
                log.info("更新配送员状态到数据库, courierId: {}", message.getCourierId());
            }

        } catch (Exception e) {
            log.error("处理配送员状态同步消息失败, courierId: {}", message.getCourierId(), e);
            // 这里可以选择重试或记录到死信队列
        }
    }
}
