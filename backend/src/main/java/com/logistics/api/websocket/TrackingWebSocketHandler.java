package com.logistics.api.websocket;

import com.logistics.api.dto.RealTimeLocationDTO;
import com.logistics.api.model.LogisticsEvent;
import com.logistics.api.service.TrackingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
public class TrackingWebSocketHandler {

    @Autowired
    @Lazy
    private TrackingService trackingService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/tracking/location")
    @SendTo("/topic/tracking/{orderId}")
    public RealTimeLocationDTO handleLocationUpdate(@Payload Map<String, Object> payload) {
        try {
            Long orderId = Long.parseLong(payload.get("orderId").toString());
            Long courierId = payload.get("courierId") != null ?
                    Long.parseLong(payload.get("courierId").toString()) : null;
            BigDecimal latitude = new BigDecimal(payload.get("latitude").toString());
            BigDecimal longitude = new BigDecimal(payload.get("longitude").toString());

            RealTimeLocationDTO dto = trackingService.uploadRealTimeLocation(
                    orderId, courierId, latitude, longitude, null, null);

            log.debug("WebSocket位置更新: orderId={}, lat={}, lng={}", orderId, latitude, longitude);
            return dto;
        } catch (Exception e) {
            log.error("WebSocket位置更新处理失败", e);
            return null;
        }
    }

    @MessageMapping("/tracking/iot")
    @SendTo("/topic/tracking/{orderId}")
    public RealTimeLocationDTO handleIoTUpdate(@Payload Map<String, Object> payload) {
        try {
            Long orderId = Long.parseLong(payload.get("orderId").toString());
            String deviceId = payload.get("deviceId").toString();
            BigDecimal latitude = new BigDecimal(payload.get("latitude").toString());
            BigDecimal longitude = new BigDecimal(payload.get("longitude").toString());

            trackingService.createTrackingRecordFromIoT(
                    orderId, deviceId, latitude.doubleValue(), longitude.doubleValue(), null, null);

            RealTimeLocationDTO dto = RealTimeLocationDTO.builder()
                    .orderId(orderId)
                    .latitude(latitude)
                    .longitude(longitude)
                    .type("iot_update")
                    .build();

            messagingTemplate.convertAndSend("/topic/tracking/" + orderId, dto);

            log.debug("IoT数据更新: orderId={}, deviceId={}", orderId, deviceId);
            return dto;
        } catch (Exception e) {
            log.error("IoT数据更新处理失败", e);
            return null;
        }
    }

    public void sendOrderStatusUpdate(Long orderId, String status, String description) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "status_update");
            message.put("orderId", orderId);
            message.put("status", status);
            message.put("description", description);
            message.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/order/" + orderId, message);
            log.debug("订单状态更新推送: orderId={}, status={}", orderId, status);
        } catch (Exception e) {
            log.error("推送订单状态更新失败, orderId: {}", orderId, e);
        }
    }

    public void sendLogisticsEventUpdate(Long orderId, LogisticsEvent event) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "logistics_event");
            message.put("orderId", orderId);
            message.put("status", event.getStatus());
            message.put("description", event.getDescription());
            message.put("location", event.getLocation());
            message.put("timestamp", event.getCreateTime());

            messagingTemplate.convertAndSend("/topic/order/" + orderId, message);
            log.debug("物流事件推送: orderId={}, status={}", orderId, event.getStatus());
        } catch (Exception e) {
            log.error("推送物流事件失败, orderId: {}", orderId, e);
        }
    }

    public void sendLocationUpdate(Long orderId, Double latitude, Double longitude) {
        try {
            RealTimeLocationDTO dto = RealTimeLocationDTO.builder()
                    .orderId(orderId)
                    .latitude(BigDecimal.valueOf(latitude))
                    .longitude(BigDecimal.valueOf(longitude))
                    .timestamp(java.time.LocalDateTime.now())
                    .type("location_update")
                    .build();

            messagingTemplate.convertAndSend("/topic/tracking/" + orderId, dto);
            log.debug("位置更新推送: orderId={}, lat={}, lng={}", orderId, latitude, longitude);
        } catch (Exception e) {
            log.error("推送位置更新失败, orderId: {}", orderId, e);
        }
    }

    public void sendCarrierUpdate(Long orderId, Map<String, Object> carrierData) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "carrier_update");
            message.put("orderId", orderId);
            message.putAll(carrierData);

            messagingTemplate.convertAndSend("/topic/order/" + orderId, message);
            log.debug("物流商数据更新推送: orderId={}", orderId);
        } catch (Exception e) {
            log.error("推送物流商数据失败, orderId: {}", orderId, e);
        }
    }

    public void notifyUser(Long userId, String type, Map<String, Object> data) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", type);
            message.putAll(data);
            message.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/user/" + userId, message);
            log.debug("用户通知推送: userId={}, type={}", userId, type);
        } catch (Exception e) {
            log.error("推送用户通知失败, userId: {}", userId, e);
        }
    }

    public void notifyCourierNewOrder(Long courierId, Long orderId, String orderNo) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "new_order");
            message.put("orderId", orderId);
            message.put("orderNo", orderNo);
            message.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/courier/" + courierId, message);
            log.debug("配送员新订单通知: courierId={}, orderId={}", courierId, orderId);
        } catch (Exception e) {
            log.error("推送配送员新订单通知失败, courierId: {}", courierId, e);
        }
    }
}
