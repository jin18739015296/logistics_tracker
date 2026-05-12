package com.logistics.api.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.api.common.BusinessException;
import com.logistics.api.common.ErrorCode;
import com.logistics.api.dto.RealTimeLocationDTO;
import com.logistics.api.dto.TrackPointDTO;
import com.logistics.api.mapper.DeliveryTaskMapper;
import com.logistics.api.mapper.OrderMapper;
import com.logistics.api.mapper.OrderTrackMapper;
import com.logistics.api.mapper.UserMapper;
import com.logistics.api.model.DeliveryTask;
import com.logistics.api.model.Order;
import com.logistics.api.model.OrderTrack;
import com.logistics.api.model.TrackingRecord;
import com.logistics.api.model.User;
import com.logistics.api.service.TrackingService;
import com.logistics.api.util.KalmanFilterUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TrackingServiceImpl implements TrackingService {

    @Autowired
    private OrderTrackMapper orderTrackMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private DeliveryTaskMapper deliveryTaskMapper;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TRACK_CACHE_PREFIX = "track:order:";
    private static final String TRACK_LATEST_PREFIX = "track:latest:";
    private static final String TRACK_KALMAN_PREFIX = "track:kalman:";
    private static final long CACHE_EXPIRE_HOURS = 2;

    // 过滤阈值：如果距离变化小于5米，且时间间隔小于30秒，则认为是静止的噪点，丢弃
    private static final double MIN_DISTANCE_THRESHOLD = 5.0; // 米
    private static final long MIN_TIME_THRESHOLD = 30000; // 毫秒

    private boolean isRedisAvailable() {
        return redisTemplate != null;
    }

    private void cacheTrackPoint(Long orderId, OrderTrack track) {
        if (!isRedisAvailable()) {
            return;
        }
        String key = TRACK_CACHE_PREFIX + orderId;
        String point = String.format("%s,%s,%d", 
            track.getLatitude(), track.getLongitude(), 
            System.currentTimeMillis());

        redisTemplate.opsForList().rightPush(key, point);
        redisTemplate.expire(key, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

        String latestKey = TRACK_LATEST_PREFIX + orderId;
        redisTemplate.opsForValue().set(latestKey, point, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
    }

    @Override
    public List<OrderTrack> getOrderTracks(Long orderId) {
        log.debug("获取订单轨迹历史, orderId: {}", orderId);

        List<OrderTrack> fromDb = orderTrackMapper.selectByOrderId(orderId);
        if (!isRedisAvailable()) {
            return fromDb;
        }
        String cacheKey = TRACK_CACHE_PREFIX + orderId;
        Long cacheSize = redisTemplate.opsForList().size(cacheKey);
        List<OrderTrack> fromCache = (cacheSize != null && cacheSize > 0) ? getCachedTracks(orderId) : new ArrayList<>();

        if (fromCache.isEmpty()) {
            return fromDb;
        }
        if (fromDb.isEmpty()) {
            return fromCache;
        }

        List<OrderTrack> merged = new ArrayList<>(fromDb);
        Set<String> seen = new HashSet<>();
        for (OrderTrack t : fromDb) {
            seen.add(roundedLatLngKey(t));
        }
        for (OrderTrack t : fromCache) {
            String k = roundedLatLngKey(t);
            if (!seen.contains(k)) {
                merged.add(t);
                seen.add(k);
            }
        }
        log.debug("合并 Redis+DB 轨迹, orderId={}, db={}, cache={}, merged={}",
                orderId, fromDb.size(), fromCache.size(), merged.size());
        return merged;
    }

    private static String roundedLatLngKey(OrderTrack t) {
        if (t.getLatitude() == null || t.getLongitude() == null) {
            return "";
        }
        return String.format(Locale.US, "%.5f,%.5f",
                t.getLatitude().doubleValue(), t.getLongitude().doubleValue());
    }

    private List<OrderTrack> getCachedTracks(Long orderId) {
        if (!isRedisAvailable()) {
            return new ArrayList<>();
        }
        String key = TRACK_CACHE_PREFIX + orderId;
        List<String> points = redisTemplate.opsForList().range(key, 0, -1);

        List<OrderTrack> tracks = new ArrayList<>();
        if (points != null) {
            for (int i = 0; i < points.size(); i++) {
                String[] parts = points.get(i).split(",");
                if (parts.length >= 2) {
                    OrderTrack track = new OrderTrack();
                    track.setOrderId(orderId);
                    track.setLatitude(new BigDecimal(parts[0]));
                    track.setLongitude(new BigDecimal(parts[1]));
                    tracks.add(track);
                }
            }
        }
        return tracks;
    }

    @Override
    public TrackingRecord updateRealTimeLocation(Long orderId, Double latitude, Double longitude, String location) {
        OrderTrack track = new OrderTrack();
        track.setOrderId(orderId);
        track.setLatitude(BigDecimal.valueOf(latitude));
        track.setLongitude(BigDecimal.valueOf(longitude));
        track.setCreateTime(LocalDateTime.now());
        orderTrackMapper.insert(track);

        cacheTrackPoint(orderId, track);

        TrackingRecord record = new TrackingRecord();
        record.setOrderId(orderId);
        record.setLatitude(BigDecimal.valueOf(latitude));
        record.setLongitude(BigDecimal.valueOf(longitude));
        record.setLocation(location);
        return record;
    }

    @Override
    public TrackingRecord createTrackingRecordFromIoT(Long orderId, String deviceId, Double latitude, Double longitude, Double temperature, Double humidity) {
        OrderTrack track = new OrderTrack();
        track.setOrderId(orderId);
        track.setLatitude(BigDecimal.valueOf(latitude));
        track.setLongitude(BigDecimal.valueOf(longitude));
        track.setCreateTime(LocalDateTime.now());
        orderTrackMapper.insert(track);

        cacheTrackPoint(orderId, track);

        TrackingRecord record = new TrackingRecord();
        record.setOrderId(orderId);
        record.setLatitude(BigDecimal.valueOf(latitude));
        record.setLongitude(BigDecimal.valueOf(longitude));
        return record;
    }

    @Override
    public TrackingRecord getLatestTrackingRecord(Long orderId) {
        if (isRedisAvailable()) {
            String latestKey = TRACK_LATEST_PREFIX + orderId;
            String latestPoint = redisTemplate.opsForValue().get(latestKey);

            if (latestPoint != null) {
                try {
                    RealTimeLocationDTO dto = objectMapper.readValue(latestPoint, RealTimeLocationDTO.class);
                    TrackingRecord record = new TrackingRecord();
                    record.setOrderId(orderId);
                    record.setLatitude(dto.getLatitude());
                    record.setLongitude(dto.getLongitude());
                    return record;
                } catch (Exception e) {
                    log.warn("解析最新位置缓存失败", e);
                }
            }
        }

        List<OrderTrack> tracks = orderTrackMapper.selectByOrderId(orderId);
        if (tracks != null && !tracks.isEmpty()) {
            OrderTrack latest = tracks.get(tracks.size() - 1);
            TrackingRecord record = new TrackingRecord();
            record.setOrderId(latest.getOrderId());
            record.setLatitude(latest.getLatitude());
            record.setLongitude(latest.getLongitude());
            return record;
        }
        return null;
    }

    @Override
    public RealTimeLocationDTO uploadRealTimeLocation(Long orderId, Long courierId, 
            BigDecimal latitude, BigDecimal longitude, Double speed, Double direction) {

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        DeliveryTask task = deliveryTaskMapper.selectByCourierIdAndOrderId(courierId,orderId);
        if (task == null || !courierId.equals(task.getCourierId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅本单负责配送员可上报位置");
        }
        
        double rawLat = latitude.doubleValue();
        double rawLng = longitude.doubleValue();
        
        double filteredLat = rawLat;
        double filteredLng = rawLng;
        
        long currentTime = System.currentTimeMillis();
        boolean shouldDiscard = false;

        if (isRedisAvailable()) {
            try {
                // 1. 卡尔曼滤波降噪
                String kalmanKey = TRACK_KALMAN_PREFIX + orderId;
                String kalmanStateStr = redisTemplate.opsForValue().get(kalmanKey);
                KalmanFilterUtil.KalmanState prevState = null;
                
                if (kalmanStateStr != null) {
                    prevState = objectMapper.readValue(kalmanStateStr, KalmanFilterUtil.KalmanState.class);
                }
                
                KalmanFilterUtil.KalmanState newState = KalmanFilterUtil.filter(prevState, rawLat, rawLng);
                filteredLat = newState.latX;
                filteredLng = newState.lngX;
                
                redisTemplate.opsForValue().set(kalmanKey, objectMapper.writeValueAsString(newState), CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                
                // 2. 距离与时间阈值过滤（聚合静止点）
                String latestKey = TRACK_LATEST_PREFIX + orderId;
                String latestPointStr = redisTemplate.opsForValue().get(latestKey);
                
                if (latestPointStr != null) {
                    RealTimeLocationDTO lastDto = objectMapper.readValue(latestPointStr, RealTimeLocationDTO.class);
                    double dist = KalmanFilterUtil.calculateDistance(filteredLat, filteredLng, lastDto.getLatitude().doubleValue(), lastDto.getLongitude().doubleValue());
                    // 假设前端没有传时间戳，我们用当前时间减去上次缓存的时间 (这里简化处理，如果DTO里有时间可以比较)
                    // 如果距离太小，视为噪点或静止，丢弃（不存入历史轨迹，但仍可推给前端或直接不推）
                    if (dist < MIN_DISTANCE_THRESHOLD) {
                        shouldDiscard = true;
                    }
                }
            } catch (Exception e) {
                log.error("轨迹滤波处理异常", e);
            }
        }

        User courier = userMapper.selectById(courierId);
        String courierName = courier != null ? courier.getName() : "配送员";

        RealTimeLocationDTO dto = RealTimeLocationDTO.builder()
                .orderId(orderId)
                .courierId(courierId)
                .courierName(courierName)
                .latitude(BigDecimal.valueOf(filteredLat))
                .longitude(BigDecimal.valueOf(filteredLng))
                .speed(speed)
                .direction(direction)
                .timestamp(LocalDateTime.now())
                .type("location_update")
                .build();

        // 5. 存入 Redis
        if (isRedisAvailable()) {
            try {
                String dtoStr = objectMapper.writeValueAsString(dto);
                String latestKey = TRACK_LATEST_PREFIX + orderId;
                redisTemplate.opsForValue().set(latestKey, dtoStr, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                
                // 只有当不被丢弃时，才加入到历史轨迹列表 (模拟 GEO 聚合)
                if (!shouldDiscard) {
                    String key = TRACK_CACHE_PREFIX + orderId;
                    String point = String.format("%s,%s,%d", filteredLat, filteredLng, currentTime);
                    redisTemplate.opsForList().rightPush(key, point);
                    redisTemplate.expire(key, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
                }
            } catch (Exception e) {
                log.error("序列化位置DTO异常", e);
            }
        }

        // 6. WebSocket 推送
        try {
            messagingTemplate.convertAndSend("/topic/tracking/" + orderId, dto);
            log.debug("位置更新推送: orderId={}, lat={}, lng={}", orderId, filteredLat, filteredLng);
        } catch (Exception e) {
            log.error("推送位置更新失败, orderId: {}", orderId, e);
        }

        return dto;
    }

    @Async
    public void persistCachedTracks(Long orderId) {
        log.info("持久化缓存轨迹到数据库, orderId: {}", orderId);

        if (!isRedisAvailable()) {
            return;
        }

        String key = TRACK_CACHE_PREFIX + orderId;
        List<String> points = redisTemplate.opsForList().range(key, 0, -1);

        if (points == null || points.isEmpty()) {
            return;
        }

        List<OrderTrack> tracks = new ArrayList<>();
        for (String point : points) {
            String[] parts = point.split(",");
            if (parts.length >= 2) {
                OrderTrack track = new OrderTrack();
                track.setOrderId(orderId);
                track.setLatitude(new BigDecimal(parts[0]));
                track.setLongitude(new BigDecimal(parts[1]));
                track.setCreateTime(LocalDateTime.now());
                tracks.add(track);
            }
        }

        if (!tracks.isEmpty()) {
            orderTrackMapper.insertBatch(tracks);
            log.info("持久化轨迹完成, orderId: {}, count: {}", orderId, tracks.size());
            // 持久化后可以考虑清除缓存，或者保留一段时间
            // redisTemplate.delete(key);
        }
    }

    public List<TrackPointDTO> getTrackPoints(Long orderId) {
        List<OrderTrack> tracks = getOrderTracks(orderId);
        return tracks.stream().map(t -> TrackPointDTO.builder()
                .id(t.getId())
                .orderId(t.getOrderId())
                .latitude(t.getLatitude())
                .longitude(t.getLongitude())
                .accuracy(t.getAccuracy())
                .createTime(t.getCreateTime())
                .build()).collect(Collectors.toList());
    }

    @Override
    public RealTimeLocationDTO getLatestLocation(Long orderId) {
        if (isRedisAvailable()) {
            String latestKey = TRACK_LATEST_PREFIX + orderId;
            String latestPoint = redisTemplate.opsForValue().get(latestKey);

            if (latestPoint != null) {
                try {
                    // 尝试解析为 DTO (新逻辑)
                    return objectMapper.readValue(latestPoint, RealTimeLocationDTO.class);
                } catch (Exception e) {
                    // 兼容旧逻辑
                    String[] parts = latestPoint.split(",");
                    if (parts.length >= 2) {
                        return RealTimeLocationDTO.builder()
                                .orderId(orderId)
                                .latitude(new BigDecimal(parts[0]))
                                .longitude(new BigDecimal(parts[1]))
                                .timestamp(LocalDateTime.now())
                                .type("location_update")
                                .build();
                    }
                }
            }
        }

        List<OrderTrack> tracks = orderTrackMapper.selectByOrderId(orderId);
        if (tracks != null && !tracks.isEmpty()) {
            OrderTrack latest = tracks.get(tracks.size() - 1);
            return RealTimeLocationDTO.builder()
                    .orderId(latest.getOrderId())
                    .latitude(latest.getLatitude())
                    .longitude(latest.getLongitude())
                    .timestamp(latest.getCreateTime())
                    .type("location_update")
                    .build();
        }
        return null;
    }
}
