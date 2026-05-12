package com.logistics.api.service.impl;

import com.logistics.api.common.BusinessException;
import com.logistics.api.common.ErrorCode;
import com.logistics.api.config.RabbitMQProperties;
import com.logistics.api.dto.CourierDistanceInfo;
import com.logistics.api.enums.OrderStatus;
import com.logistics.api.mapper.CourierStatusMapper;
import com.logistics.api.mapper.DeliveryTaskMapper;
import com.logistics.api.mapper.OrderMapper;
import com.logistics.api.messaging.CourierStatusSyncMessage;
import com.logistics.api.messaging.MessageSender;
import com.logistics.api.model.CourierStatus;
import com.logistics.api.model.DeliveryTask;
import com.logistics.api.model.Order;
import com.logistics.api.service.CourierStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 配送员状态服务实现
 * 基于Redis实现高性能状态管理，通过MQ异步同步到MySQL数据库
 */
@Slf4j
@Service
public class CourierStatusServiceImpl implements CourierStatusService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private CourierStatusMapper courierStatusMapper;

    @Autowired
    private DeliveryTaskMapper deliveryTaskMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private MessageSender messageSender;

    @Autowired
    private RabbitMQProperties rabbitMQProperties;

    // Redis Key 前缀
    private static final String COURIER_STATUS_PREFIX = "courier:status:";
    private static final String COURIER_LOCATION_PREFIX = "courier:location:";
    private static final String COURIER_CITY_PREFIX = "courier:city:";
    private static final String COURIER_UPDATE_TIME_PREFIX = "courier:update:";
    private static final String AVAILABLE_COURIERS_KEY = "couriers:available";

    // Redis Geo Key - 按城市存储配送员位置
    private static final String COURIER_GEO_PREFIX = "courier:geo:";

    // 状态值
    private static final String STATUS_OFFLINE = "offline"; // 离线/下班
    private static final String STATUS_IDLE = "idle";       // 空闲，可接单
    private static final String STATUS_BUSY = "busy";       // 配送中（已开始配送）
    private static final String STATUS_FULL = "full";       // 订单已满（内部状态，前端显示为idle）

    // 自动离线时间：60分钟（与位置过期时间一致，避免过早将配送员设为离线）
    private static final long AUTO_OFFLINE_MINUTES = 60;
    // 位置过期时间：4小时（240分钟），给配送员足够缓冲时间，避免后台定时器停止导致数据过早过期
    private static final long LOCATION_EXPIRE_MINUTES = 240;

    /**
     * 统一城市名称格式：去掉"市"、"省"等后缀，避免"上海"和"上海市"不匹配
     */
    private String normalizeCityName(String city) {
        if (city == null || city.isEmpty()) {
            return city;
        }
        String normalized = city.trim();
        // 去掉常见的行政区划后缀
        String[] suffixes = {"市", "省", "自治区", "特别行政区"};
        for (String suffix : suffixes) {
            if (normalized.endsWith(suffix)) {
                normalized = normalized.substring(0, normalized.length() - suffix.length());
                break; // 只去掉第一个匹配的后缀
            }
        }
        return normalized;
    }

    @Override
    public void goOnline(Long courierId, Double latitude, Double longitude, String workCity) {
        // 统一城市名称格式：去掉"市"后缀，避免"上海"和"上海市"不匹配
        String normalizedCity = normalizeCityName(workCity);
        log.info("配送员上线, courierId: {}, city: {}, location: {}, {}", courierId, normalizedCity, latitude, longitude);

        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // ===== 关键修复：处理数据库与Redis状态不一致问题 =====
        // 1. 先查询数据库中的真实状态
        CourierStatus dbStatus = courierStatusMapper.selectById(courierId);
        log.info("数据库中配送员状态, courierId: {}, status: {}", courierId, 
                dbStatus != null ? dbStatus.getStatus() : "无记录");

        // 2. 查询配送员是否有进行中的订单（用于恢复订单计数到Redis）
        List<DeliveryTask> activeTasks = deliveryTaskMapper.selectByCourierIdAndStatuses(courierId,
                List.of("awaiting_courier_confirm", "awaiting_pickup", "picked_up", "in_transit"));
        int activeOrderCount = activeTasks != null ? activeTasks.size() : 0;
        log.info("配送员进行中订单数, courierId: {}, count: {}", courierId, activeOrderCount);

        // 3. 确定正确的初始状态
        // 注意：上线时强制设为idle，busy状态只在开始配送时设置
        String initialStatus = STATUS_IDLE;
        boolean shouldBeAvailable = true;
        log.info("配送员上线，设为idle状态");

        // 5. 存储到Redis（高性能缓存）- 同步操作，保证实时性
        // 存储状态（使用正确的状态，而不是强制idle）
        redisTemplate.opsForValue().set(
                COURIER_STATUS_PREFIX + courierId,
                initialStatus,
                LOCATION_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // 存储位置
        redisTemplate.opsForValue().set(
                COURIER_LOCATION_PREFIX + courierId,
                latitude + "," + longitude,
                LOCATION_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // 存储工作城市（使用统一后的城市名）
        redisTemplate.opsForValue().set(
                COURIER_CITY_PREFIX + courierId,
                normalizedCity,
                LOCATION_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // 存储更新时间
        redisTemplate.opsForValue().set(
                COURIER_UPDATE_TIME_PREFIX + courierId,
                now,
                LOCATION_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // 只有空闲状态才添加到可分配列表
        if (shouldBeAvailable && activeOrderCount < 5) {
            redisTemplate.opsForSet().add(AVAILABLE_COURIERS_KEY, courierId.toString());
            log.info("配送员添加到可分配列表, courierId: {}", courierId);
        } else {
            // 忙碌状态从可分配列表移除
            redisTemplate.opsForSet().remove(AVAILABLE_COURIERS_KEY, courierId.toString());
            log.info("配送员从可分配列表移除（忙碌或有进行中订单）, courierId: {}", courierId);
        }

        // 6. 添加到Redis Geo（用于高效的范围查询，使用统一后的城市名）
        String geoKey = COURIER_GEO_PREFIX + normalizedCity;
        redisTemplate.opsForGeo().add(geoKey, new Point(longitude, latitude), courierId.toString());
        redisTemplate.expire(geoKey, LOCATION_EXPIRE_MINUTES, TimeUnit.MINUTES);

        // 7. 恢复订单计数到Redis
        if (activeOrderCount > 0) {
            redisTemplate.opsForValue().set(
                    "courier:order_count:" + courierId,
                    String.valueOf(activeOrderCount),
                    4,
                    TimeUnit.HOURS
            );
            // 恢复配送订单集合
            for (DeliveryTask task : activeTasks) {
                redisTemplate.opsForSet().add("courier:orders:" + courierId, task.getOrderId().toString());
            }
            redisTemplate.expire("courier:orders:" + courierId, 4, TimeUnit.HOURS);
        }

        // 8. 同步落库：管理员「在线状态」表立即反映本次上线坐标（不靠 MQ 时序）
        try {
            persistCourierOnlineToDb(courierId, latitude, longitude, initialStatus, activeOrderCount);
        } catch (Exception e) {
            log.error("上线同步数据库失败, courierId: {}", courierId, e);
        }

        // 9. 发送MQ消息异步补偿（消费者按 ONLINE 走全量更新）
        try {
            CourierStatusSyncMessage syncMessage = CourierStatusSyncMessage.builder()
                    .courierId(courierId)
                    .status(initialStatus)
                    .latitude(latitude)
                    .longitude(longitude)
                    .workCity(workCity)
                    .currentOrderCount(activeOrderCount)
                    .messageType("ONLINE")
                    .timestamp(LocalDateTime.now())
                    .isNewRecord(false)
                    .build();

            // 使用 MessageSender 组件发送消息（带本地消息表保障）
            messageSender.sendCourierStatusMessage(syncMessage, rabbitMQProperties.getCourierStatusSync());
            log.info("发送配送员上线同步消息到MQ, courierId: {}, status: {}, orders: {}", 
                    courierId, initialStatus, activeOrderCount);
        } catch (Exception e) {
            log.error("发送MQ消息失败, courierId: {}", courierId, e);
            // MQ发送失败不影响Redis操作，继续执行
        }

        log.info("配送员上线成功, courierId: {}, status: {}, location: {},{}, activeOrders: {}"
                , courierId, initialStatus, latitude, longitude, activeOrderCount);
    }

    /** 同步写入 MySQL，避免仅靠 MQ 时后台「在线状态表」滞后或与 Redis 不一致 */
    private void persistCourierOnlineToDb(Long courierId, Double latitude, Double longitude,
                                          String status, int activeOrderCount) {
        java.math.BigDecimal latBd = java.math.BigDecimal.valueOf(latitude);
        java.math.BigDecimal lngBd = java.math.BigDecimal.valueOf(longitude);
        CourierStatus cs = new CourierStatus();
        cs.setCourierId(courierId);
        cs.setStatus(status);
        cs.setCurrentLat(latBd);
        cs.setCurrentLng(lngBd);
        cs.setCurrentOrderCount(activeOrderCount);
        CourierStatus existing = courierStatusMapper.selectById(courierId);
        if (existing != null) {
            courierStatusMapper.update(cs);
        } else {
            courierStatusMapper.insert(cs);
        }
    }

    @Override
    public void goOffline(Long courierId) {
        log.info("配送员下线, courierId: {}", courierId);

        // 1. 检查是否有进行中的订单
        List<DeliveryTask> activeTasks = deliveryTaskMapper.selectByCourierIdAndStatuses(courierId,
                List.of("awaiting_courier_confirm", "awaiting_pickup", "picked_up", "in_transit"));
        int activeOrderCount = activeTasks != null ? activeTasks.size() : 0;

        if (activeOrderCount > 0) {
            log.warn("配送员有{}个进行中订单，不允许下线, courierId: {}", activeOrderCount, courierId);
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, 
                    "您有" + activeOrderCount + "个进行中的订单，请先完成或转派后再下线");
        }

        // 获取工作城市（用于清理Geo数据）
        String workCity = redisTemplate.opsForValue().get(COURIER_CITY_PREFIX + courierId);

        // 2. 更新Redis状态为离线
        redisTemplate.opsForValue().set(
                COURIER_STATUS_PREFIX + courierId,
                STATUS_OFFLINE,
                1,
                TimeUnit.DAYS
        );

        // 从可分配列表移除
        redisTemplate.opsForSet().remove(AVAILABLE_COURIERS_KEY, courierId.toString());

        // 从Redis Geo中移除
        if (workCity != null) {
            String geoKey = COURIER_GEO_PREFIX + workCity;
            redisTemplate.opsForGeo().remove(geoKey, courierId.toString());
        }

        // 位置信息保留一段时间，便于查看历史
        redisTemplate.expire(COURIER_LOCATION_PREFIX + courierId, 1, TimeUnit.DAYS);

        // 3. 同步更新数据库（落库）
        try {
            CourierStatus status = new CourierStatus();
            status.setCourierId(courierId);
            status.setStatus(STATUS_OFFLINE);
            status.setCurrentOrderCount(0);
            
            CourierStatus existing = courierStatusMapper.selectById(courierId);
            if (existing != null) {
                courierStatusMapper.updateOfflineState(courierId, STATUS_OFFLINE, 0);
            } else {
                courierStatusMapper.insert(status);
            }
            
            // 发送MQ消息同步
            CourierStatusSyncMessage syncMessage = CourierStatusSyncMessage.builder()
                    .courierId(courierId)
                    .status(STATUS_OFFLINE)
                    .currentOrderCount(0)
                    .messageType("OFFLINE")
                    .timestamp(LocalDateTime.now())
                    .isNewRecord(existing == null)
                    .build();
            
            messageSender.sendCourierStatusMessage(syncMessage, rabbitMQProperties.getCourierStatusSync());
            log.info("配送员下线落库成功, courierId: {}", courierId);
        } catch (Exception e) {
            log.error("配送员下线落库失败, courierId: {}", courierId, e);
        }

        log.info("配送员下线成功, courierId: {}", courierId);
    }

    @Override
    public void updateLocation(Long courierId, Double latitude, Double longitude) {
        // 只更新空闲配送员的位置
        String status = redisTemplate.opsForValue().get(COURIER_STATUS_PREFIX + courierId);
        if (!STATUS_IDLE.equals(status)) {
            log.debug("配送员非空闲状态，跳过位置更新, courierId: {}", courierId);
            return;
        }

        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // 获取工作城市（用于更新Geo）
        String workCity = redisTemplate.opsForValue().get(COURIER_CITY_PREFIX + courierId);

        // 1. 更新Redis - 同步操作，保证实时性
        // 更新位置
        redisTemplate.opsForValue().set(
                COURIER_LOCATION_PREFIX + courierId,
                latitude + "," + longitude,
                LOCATION_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // 更新时间
        redisTemplate.opsForValue().set(
                COURIER_UPDATE_TIME_PREFIX + courierId,
                now,
                LOCATION_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // 2. 更新Redis Geo位置
        if (workCity != null) {
            String geoKey = COURIER_GEO_PREFIX + workCity;
            redisTemplate.opsForGeo().add(geoKey, new Point(longitude, latitude), courierId.toString());
            redisTemplate.expire(geoKey, LOCATION_EXPIRE_MINUTES, TimeUnit.MINUTES);
        }

        // 3. 发送MQ消息异步同步到数据库（不阻塞主流程）
        try {
            CourierStatusSyncMessage syncMessage = CourierStatusSyncMessage.builder()
                    .courierId(courierId)
                    .latitude(latitude)
                    .longitude(longitude)
                    .messageType("UPDATE_LOCATION")
                    .timestamp(LocalDateTime.now())
                    .isNewRecord(false)
                    .build();

            // 使用 MessageSender 组件发送消息（带本地消息表保障）
            messageSender.sendCourierStatusMessage(syncMessage, rabbitMQProperties.getCourierStatusSync());
            log.debug("发送位置更新同步消息到MQ, courierId: {}", courierId);
        } catch (Exception e) {
            log.error("发送MQ消息失败, courierId: {}", courierId, e);
            // MQ发送失败不影响Redis操作
        }

        log.debug("配送员位置更新, courierId: {}, location: {},{}"
                , courierId, latitude, longitude);
    }

    @Override
    public void startDelivery(Long courierId, Long orderId) {
        log.info("配送员开始配送, courierId: {}, orderId: {}", courierId, orderId);

        // 更新状态为配送中（busy）- 只要开始配送就进入busy状态
        redisTemplate.opsForValue().set(
                COURIER_STATUS_PREFIX + courierId,
                STATUS_BUSY,
                LOCATION_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );
        // 从可分配列表移除（配送中不可分配新订单）
        redisTemplate.opsForSet().remove(AVAILABLE_COURIERS_KEY, courierId.toString());

        // 同步更新数据库状态
        CourierStatus cs = new CourierStatus();
        cs.setCourierId(courierId);
        cs.setStatus(STATUS_BUSY);
        cs.setUpdateTime(LocalDateTime.now());
        CourierStatus existing = courierStatusMapper.selectById(courierId);
        if (existing != null) {
            courierStatusMapper.update(cs);
        } else {
            courierStatusMapper.insert(cs);
        }

        if (orderId == null) {
            log.info("配送员开始配送（无单订单上下文，仅 busy），请配合 syncActiveOrderKeysFromDb, courierId: {}", courierId);
            return;
        }

        int currentCount = getCurrentOrderCount(courierId);
        int newCount = currentCount + 1;

        redisTemplate.opsForSet().add("courier:orders:" + courierId, orderId.toString());
        redisTemplate.expire("courier:orders:" + courierId, 4, TimeUnit.HOURS);

        redisTemplate.opsForValue().set(
                "courier:order_count:" + courierId,
                String.valueOf(newCount),
                4,
                TimeUnit.HOURS
        );

        // 同步更新数据库订单数
        courierStatusMapper.updateOrderCount(courierId, newCount);

        log.info("配送员开始配送，状态更新为busy, courierId: {}, currentOrders: {}", courierId, newCount);
    }

    @Override
    public void addOrderToCourier(Long courierId, Long orderId) {
        log.info("订单加入配送员集合, courierId: {}, orderId: {}", courierId, orderId);

        int currentCount = getCurrentOrderCount(courierId);
        int newCount = currentCount + 1;

        redisTemplate.opsForSet().add("courier:orders:" + courierId, orderId.toString());
        redisTemplate.expire("courier:orders:" + courierId, 4, TimeUnit.HOURS);

        redisTemplate.opsForValue().set(
                "courier:order_count:" + courierId,
                String.valueOf(newCount),
                4,
                TimeUnit.HOURS
        );

        // 如果满单（>=5），从可分配列表移除；否则保持 idle 可分配
        if (newCount >= 5) {
            redisTemplate.opsForSet().remove(AVAILABLE_COURIERS_KEY, courierId.toString());
            log.info("配送员订单已满，从可分配列表移除, courierId: {}, count: {}", courierId, newCount);
        }

        log.info("订单加入配送员集合完成, courierId: {}, currentOrders: {}", courierId, newCount);
    }

    @Override
    public void syncActiveOrderKeysFromDb(Long courierId) {
        List<DeliveryTask> activeTasks = deliveryTaskMapper.selectByCourierIdAndStatuses(courierId,
                List.of("awaiting_courier_confirm", "awaiting_pickup", "picked_up", "in_transit"));
        List<Long> orderIds = activeTasks == null ? List.of() : activeTasks.stream()
                .map(DeliveryTask::getOrderId)
                .filter(Objects::nonNull)
                .toList();
        int n = orderIds.size();
        String ordersKey = "courier:orders:" + courierId;

        redisTemplate.delete(ordersKey);

        if (n > 0) {
            for (Long orderId : orderIds) {
                redisTemplate.opsForSet().add(ordersKey, orderId.toString());
            }
            redisTemplate.expire(ordersKey, 4, TimeUnit.HOURS);
            redisTemplate.opsForValue().set(
                    "courier:order_count:" + courierId,
                    String.valueOf(n),
                    4,
                    TimeUnit.HOURS);
        } else {
            redisTemplate.delete("courier:order_count:" + courierId);
        }

        log.info("已从数据库同步配送员进行中订单到 Redis, courierId: {}, count: {}", courierId, n);
    }

    @Override
    public void finishDelivery(Long courierId, Long orderId) {
        log.info("配送员完成配送, courierId: {}, orderId: {}", courierId, orderId);

        // 从配送订单集合移除
        redisTemplate.opsForSet().remove("courier:orders:" + courierId, orderId.toString());

        // 以数据库为准，查询配送员是否还有未送达的订单
        List<Order> activeOrders = orderMapper.selectByCourierId(courierId).stream()
                .filter(order -> {
                    String st = order.getStatus();
                    return OrderStatus.AWAITING_PICKUP.getCode().equals(st)
                            || OrderStatus.PICKED_UP.getCode().equals(st)
                            || OrderStatus.IN_TRANSIT.getCode().equals(st);
                })
                .toList();
        int newCount = activeOrders.size();

        // 同步 courier:orders set（以数据库为准）
        String ordersKey = "courier:orders:" + courierId;
        redisTemplate.delete(ordersKey);
        if (newCount > 0) {
            for (Order order : activeOrders) {
                redisTemplate.opsForSet().add(ordersKey, order.getId().toString());
            }
            redisTemplate.expire(ordersKey, 4, TimeUnit.HOURS);
        }

        // 更新订单计数
        if (newCount > 0) {
            redisTemplate.opsForValue().set(
                    "courier:order_count:" + courierId,
                    String.valueOf(newCount),
                    4,
                    TimeUnit.HOURS
            );
        } else {
            // 没有订单了，删除计数
            redisTemplate.delete("courier:order_count:" + courierId);
        }

        // 获取当前状态
        String status = redisTemplate.opsForValue().get(COURIER_STATUS_PREFIX + courierId);
        
        // 如果当前是配送中（busy）状态，且没有剩余订单了，则恢复为空闲
        if (STATUS_BUSY.equals(status) && newCount == 0) {
            redisTemplate.opsForValue().set(
                    COURIER_STATUS_PREFIX + courierId,
                    STATUS_IDLE,
                    LOCATION_EXPIRE_MINUTES,
                    TimeUnit.MINUTES
            );
            // 添加回可分配列表（如果未满单）
            if (newCount < 5) {
                redisTemplate.opsForSet().add(AVAILABLE_COURIERS_KEY, courierId.toString());
            }
            // 同步更新数据库状态为 idle
            CourierStatus cs = new CourierStatus();
            cs.setCourierId(courierId);
            cs.setStatus(STATUS_IDLE);
            cs.setCurrentOrderCount(0);
            cs.setUpdateTime(LocalDateTime.now());
            CourierStatus existing = courierStatusMapper.selectById(courierId);
            if (existing != null) {
                courierStatusMapper.update(cs);
            } else {
                courierStatusMapper.insert(cs);
            }
            log.info("配送员完成所有配送，状态恢复为idle, courierId: {}", courierId);
        } else if (STATUS_BUSY.equals(status) && newCount > 0) {
            // 还有订单在配送中，保持busy状态，但更新订单数
            courierStatusMapper.updateOrderCount(courierId, newCount);
            log.info("配送员还有{}个订单在配送中，保持busy状态, courierId: {}", newCount, courierId);
        }

        log.info("配送员完成配送，当前订单数: {}, courierId: {}", newCount, courierId);
    }

    @Override
    public boolean isAvailableForDispatch(Long courierId) {
        // 检查状态是否为空闲（idle 或 full 都可以，full只是不能分配但显示为空闲）
        String status = redisTemplate.opsForValue().get(COURIER_STATUS_PREFIX + courierId);
        if (!STATUS_IDLE.equals(status) && !STATUS_FULL.equals(status)) {
            return false;
        }

        // 检查订单数是否已满（即使状态是idle，如果订单数达到上限也不能分配）
        int orderCount = getCurrentOrderCount(courierId);
        if (orderCount >= 5) {
            return false;
        }

        // 检查位置是否存在（避免 COURIER_LOCATION_PREFIX 过期）
        String locationStr = getCourierLocation(courierId);
        if (locationStr == null || locationStr.isEmpty()) {
            return false;
        }

        // 检查是否在可分配列表中
        Boolean isMember = redisTemplate.opsForSet().isMember(AVAILABLE_COURIERS_KEY, courierId.toString());
        return Boolean.TRUE.equals(isMember);
    }

    @Override
    public String getCourierLocation(Long courierId) {
        return redisTemplate.opsForValue().get(COURIER_LOCATION_PREFIX + courierId);
    }

    @Override
    public List<Long> getAvailableCourierIds(String city) {
        Set<String> courierIds = redisTemplate.opsForSet().members(AVAILABLE_COURIERS_KEY);
        if (courierIds == null || courierIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> result = new ArrayList<>();
        for (String courierIdStr : courierIds) {
            Long courierId = Long.valueOf(courierIdStr);

            // 检查状态
            if (!isAvailableForDispatch(courierId)) {
                continue;
            }

            // 检查位置是否存在（避免 COURIER_LOCATION_PREFIX 过期但 AVAILABLE_COURIERS_KEY 未清理）
            String locationStr = getCourierLocation(courierId);
            if (locationStr == null || locationStr.isEmpty()) {
                log.warn("配送员位置已过期，从可分配列表移除, courierId: {}", courierId);
                redisTemplate.opsForSet().remove(AVAILABLE_COURIERS_KEY, courierIdStr);
                continue;
            }

            // 如果指定了城市，检查工作城市是否匹配（使用统一后的城市名比较）
            if (city != null && !city.isEmpty()) {
                String workCity = redisTemplate.opsForValue().get(COURIER_CITY_PREFIX + courierId);
                String normalizedWorkCity = normalizeCityName(workCity);
                String normalizedCity = normalizeCityName(city);
                if (!normalizedCity.equals(normalizedWorkCity)) {
                    continue;
                }
            }

            result.add(courierId);
        }

        return result;
    }

    @Override
    public void cleanupInactiveCouriers() {
        log.info("开始清理不活跃配送员状态");

        Set<String> courierIds = redisTemplate.opsForSet().members(AVAILABLE_COURIERS_KEY);
        if (courierIds == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int cleanupCount = 0;

        for (String courierIdStr : courierIds) {
            Long courierId = Long.valueOf(courierIdStr);

            // 获取最后更新时间
            String updateTimeStr = redisTemplate.opsForValue()
                    .get(COURIER_UPDATE_TIME_PREFIX + courierId);

            if (updateTimeStr == null) {
                // 无更新时间，视为离线
                goOffline(courierId);
                cleanupCount++;
                continue;
            }

            LocalDateTime updateTime = LocalDateTime.parse(updateTimeStr);
            Duration duration = Duration.between(updateTime, now);

            // 超过30分钟未更新，视为离线
            if (duration.toMinutes() > AUTO_OFFLINE_MINUTES) {
                log.info("配送员长时间未更新，自动离线, courierId: {}, 未更新时长: {}分钟"
                        , courierId, duration.toMinutes());
                goOffline(courierId);
                cleanupCount++;
            }
        }

        log.info("清理完成，共处理 {} 个不活跃配送员", cleanupCount);
    }

    @Override
    public int getCurrentOrderCount(Long courierId) {
        String countStr = redisTemplate.opsForValue()
                .get("courier:order_count:" + courierId);
        return countStr != null ? Integer.parseInt(countStr) : 0;
    }

    @Override
    public boolean canAcceptMoreOrders(Long courierId, int maxOrders) {
        // 检查状态是否为空闲（idle 或 full）
        String status = redisTemplate.opsForValue().get(COURIER_STATUS_PREFIX + courierId);
        if (!STATUS_IDLE.equals(status) && !STATUS_FULL.equals(status)) {
            return false;
        }

        // 检查是否达到接单上限
        int currentCount = getCurrentOrderCount(courierId);
        return currentCount < maxOrders;
    }

    @Override
    public List<Long> getCurrentOrderIds(Long courierId) {
        Set<String> orderIds = redisTemplate.opsForSet()
                .members("courier:orders:" + courierId);
        if (orderIds == null) {
            return new ArrayList<>();
        }
        return orderIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }

    @Override
    public CourierStatus getCourierStatusFromDB(Long courierId) {
        return courierStatusMapper.selectById(courierId);
    }

    @Override
    public List<CourierStatus> getAllCourierStatusFromDB() {
        // 查询所有在线或忙碌的配送员（离线的不返回）
        List<CourierStatus> idleList = courierStatusMapper.selectByStatus(STATUS_IDLE);
        List<CourierStatus> busyList = courierStatusMapper.selectByStatus(STATUS_BUSY);
        List<CourierStatus> fullList = courierStatusMapper.selectByStatus(STATUS_FULL);
        
        List<CourierStatus> result = new ArrayList<>();
        result.addAll(idleList);
        result.addAll(busyList);
        result.addAll(fullList);
        return result;
    }

    @Override
    public List<Long> getAvailableCourierIdsWithinRadius(Double latitude, Double longitude, Double radiusKm) {
        log.info("查找{}公里范围内可分配的配送员, center: {}, {}", radiusKm, latitude, longitude);

        // 获取所有可分配的配送员
        List<Long> availableCourierIds = getAvailableCourierIds(null);
        if (availableCourierIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> result = new ArrayList<>();
        for (Long courierId : availableCourierIds) {
            String locationStr = getCourierLocation(courierId);
            if (locationStr == null || locationStr.isEmpty()) {
                continue;
            }

            try {
                String[] parts = locationStr.split(",");
                double courierLat = Double.parseDouble(parts[0]);
                double courierLng = Double.parseDouble(parts[1]);

                // 计算距离（使用Haversine公式）
                double distance = calculateDistance(latitude, longitude, courierLat, courierLng);

                if (distance <= radiusKm) {
                    result.add(courierId);
                    log.debug("配送员{}在范围内，距离: {}公里", courierId, String.format("%.2f", distance));
                }
            } catch (Exception e) {
                log.warn("解析配送员位置失败, courierId: {}", courierId);
            }
        }

        log.info("找到{}个配送员在{}公里范围内", result.size(), radiusKm);
        return result;
    }

    @Override
    public List<Long> getAvailableCourierIdsWithinRadiusByCity(String city, Double latitude, Double longitude, Double radiusKm) {
        log.info("查找{}城市{}公里范围内可分配的配送员, center: {}, {}", city, radiusKm, latitude, longitude);

        if (city == null || city.isEmpty()) {
            log.warn("城市参数为空，使用全量查询");
            return getAvailableCourierIdsWithinRadius(latitude, longitude, radiusKm);
        }

        // 统一城市名称格式，确保与 goOnline 时存储的 key 一致
        String normalizedCity = normalizeCityName(city);
        String geoKey = COURIER_GEO_PREFIX + normalizedCity;

        // 使用Redis Geo查询半径范围内的配送员
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = redisTemplate.opsForGeo().radius(
                geoKey,
                new Circle(new Point(longitude, latitude), new Distance(radiusKm, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .sortAscending()
        );

        if (geoResults == null || geoResults.getContent().isEmpty()) {
            log.info("Redis Geo未找到配送员，城市: {} (normalized: {})", city, normalizedCity);
            return new ArrayList<>();
        }

        List<Long> result = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : geoResults) {
            String courierIdStr = geoResult.getContent().getName();
            Long courierId = Long.valueOf(courierIdStr);

            // 检查配送员是否真正可用（状态检查）
            if (isAvailableForDispatch(courierId)) {
                result.add(courierId);
                log.debug("配送员{}在范围内，距离: {}公里", courierId,
                        String.format("%.2f", geoResult.getDistance().getValue()));
            } else {
                log.debug("配送员{}在范围内但不可用，从Geo中移除", courierId);
                // 从Geo中移除不可用的配送员
                redisTemplate.opsForGeo().remove(geoKey, courierIdStr);
            }
        }

        log.info("找到{}个可用配送员在{}城市{}公里范围内", result.size(), city, radiusKm);
        return result;
    }

    @Override
    public List<CourierDistanceInfo> getAvailableCouriersWithDistance(String city, Double latitude, Double longitude, Double radiusKm, Integer limit) {
        log.info("获取{}城市{}公里范围内配送员及距离信息, center: {}, {}", city, radiusKm, latitude, longitude);

        if (city == null || city.isEmpty()) {
            log.warn("城市参数为空");
            return new ArrayList<>();
        }

        // 统一城市名称格式，确保与 goOnline 时存储的 key 一致
        String normalizedCity = normalizeCityName(city);
        String geoKey = COURIER_GEO_PREFIX + normalizedCity;

        // 使用Redis Geo查询
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = redisTemplate.opsForGeo().radius(
                geoKey,
                new Circle(new Point(longitude, latitude), new Distance(radiusKm, Metrics.KILOMETERS)),
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeDistance()
                        .sortAscending()
                        .limit(limit != null ? limit : 50)
        );

        if (geoResults == null || geoResults.getContent().isEmpty()) {
            return new ArrayList<>();
        }

        List<CourierDistanceInfo> result = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : geoResults) {
            String courierIdStr = geoResult.getContent().getName();
            Long courierId = Long.valueOf(courierIdStr);

            // 只返回可用的配送员
            if (isAvailableForDispatch(courierId)) {
                CourierDistanceInfo info = new CourierDistanceInfo();
                info.setCourierId(courierId);
                info.setDistanceKm(geoResult.getDistance().getValue());

                // 获取当前订单数
                info.setCurrentOrderCount(getCurrentOrderCount(courierId));

                result.add(info);
            }
        }

        return result;
    }

    /**
     * 使用Haversine公式计算两点之间的距离（公里）
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final double EARTH_RADIUS = 6371.0; // 地球半径（公里）

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    @Override
    public Map<String, Object> getCourierCurrentStatus(Long courierId, Double latitude, Double longitude) {
        log.info("获取配送员当前状态, courierId: {}, location: {}, {}", courierId, latitude, longitude);

        // 1. 以数据库为准，查询配送员状态
        CourierStatus dbStatus = courierStatusMapper.selectById(courierId);

        // 2. 查询进行中的订单数（仅用于返回信息，不影响状态判断）
        List<DeliveryTask> activeTasks = deliveryTaskMapper.selectByCourierIdAndStatuses(courierId,
                List.of("awaiting_courier_confirm", "awaiting_pickup", "picked_up", "in_transit"));
        int activeOrderCount = activeTasks != null ? activeTasks.size() : 0;

        // 3. 确定正确的状态（以数据库为准）
        String currentStatus;
        if (dbStatus != null && dbStatus.getStatus() != null) {
            // 使用数据库状态
            currentStatus = dbStatus.getStatus();
        } else {
            // 数据库无记录，默认为离线
            currentStatus = STATUS_OFFLINE;
        }

        // 4. 如果传入了位置，且当前为idle状态，才更新位置信息
        // busy状态（配送中）和offline状态不更新位置，避免干扰配送流程
        if (latitude != null && longitude != null && STATUS_IDLE.equals(currentStatus)) {
            log.info("配送员为idle状态，更新位置, courierId: {}, location: {}, {}", courierId, latitude, longitude);

            // 更新数据库位置（通过MQ异步）
            try {
                CourierStatusSyncMessage syncMessage = CourierStatusSyncMessage.builder()
                        .courierId(courierId)
                        .status(currentStatus)
                        .latitude(latitude)
                        .longitude(longitude)
                        .currentOrderCount(activeOrderCount)
                        .messageType("UPDATE_LOCATION")
                        .timestamp(LocalDateTime.now())
                        .isNewRecord(false)
                        .build();
                messageSender.sendCourierStatusMessage(syncMessage, rabbitMQProperties.getCourierStatusSync());
            } catch (Exception e) {
                log.error("发送位置更新消息失败, courierId: {}", courierId, e);
            }

            // 如果Redis中有数据，也更新Redis（保持双写一致性）
            String redisStatus = redisTemplate.opsForValue().get(COURIER_STATUS_PREFIX + courierId);
            if (redisStatus != null) {
                // Redis中有数据，更新位置和过期时间
                redisTemplate.opsForValue().set(
                        COURIER_LOCATION_PREFIX + courierId,
                        latitude + "," + longitude,
                        LOCATION_EXPIRE_MINUTES,
                        TimeUnit.MINUTES
                );
                redisTemplate.opsForValue().set(
                        COURIER_UPDATE_TIME_PREFIX + courierId,
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        LOCATION_EXPIRE_MINUTES,
                        TimeUnit.MINUTES
                );
                // 更新状态
                redisTemplate.opsForValue().set(
                        COURIER_STATUS_PREFIX + courierId,
                        currentStatus,
                        LOCATION_EXPIRE_MINUTES,
                        TimeUnit.MINUTES
                );
                // 同步可接单集合：idle 且未满单则加入，否则移除
                if (STATUS_IDLE.equals(currentStatus) && activeOrderCount < 5) {
                    redisTemplate.opsForSet().add(AVAILABLE_COURIERS_KEY, courierId.toString());
                } else {
                    redisTemplate.opsForSet().remove(AVAILABLE_COURIERS_KEY, courierId.toString());
                }
            } else {
                // Redis中没有数据，需要恢复Redis数据
                log.info("Redis中无数据，从数据库恢复, courierId: {}", courierId);
                restoreCourierToRedis(courierId, currentStatus, latitude, longitude, activeOrderCount, activeTasks);
            }
        } else if (latitude != null && longitude != null) {
            // 非idle状态，只记录日志，不更新位置
            log.debug("配送员非idle状态({})，跳过位置更新, courierId: {}", currentStatus, courierId);
        }

        // 5. 组装返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("courierId", courierId);
        result.put("status", currentStatus);
        result.put("activeOrderCount", activeOrderCount);
        result.put("isOnline", !STATUS_OFFLINE.equals(currentStatus));

        // 添加位置信息（优先从Redis获取，没有则从数据库获取）
        String locationStr = redisTemplate.opsForValue().get(COURIER_LOCATION_PREFIX + courierId);
        if (locationStr != null) {
            String[] parts = locationStr.split(",");
            result.put("latitude", Double.parseDouble(parts[0]));
            result.put("longitude", Double.parseDouble(parts[1]));
        } else if (dbStatus != null && dbStatus.getCurrentLat() != null && dbStatus.getCurrentLng() != null) {
            result.put("latitude", dbStatus.getCurrentLat());
            result.put("longitude", dbStatus.getCurrentLng());
        }

        // 添加工作城市（只从Redis获取，数据库没有该字段）
        String workCity = redisTemplate.opsForValue().get(COURIER_CITY_PREFIX + courierId);
        result.put("workCity", workCity);

        log.info("配送员当前状态, courierId: {}, status: {}, orders: {}",
                courierId, currentStatus, activeOrderCount);

        return result;
    }

    /**
     * 从数据库恢复配送员状态到Redis
     */
    private void restoreCourierToRedis(Long courierId, String status, Double latitude, Double longitude,
                                       int orderCount, List<DeliveryTask> activeTasks) {
        log.info("恢复配送员状态到Redis, courierId: {}, status: {}", courierId, status);

        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // 恢复状态
        redisTemplate.opsForValue().set(
                COURIER_STATUS_PREFIX + courierId,
                status,
                LOCATION_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // 恢复位置
        if (latitude != null && longitude != null) {
            redisTemplate.opsForValue().set(
                    COURIER_LOCATION_PREFIX + courierId,
                    latitude + "," + longitude,
                    LOCATION_EXPIRE_MINUTES,
                    TimeUnit.MINUTES
            );
        }

        // 恢复更新时间
        redisTemplate.opsForValue().set(
                COURIER_UPDATE_TIME_PREFIX + courierId,
                now,
                LOCATION_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        // 恢复订单计数
        if (orderCount > 0) {
            redisTemplate.opsForValue().set(
                    "courier:order_count:" + courierId,
                    String.valueOf(orderCount),
                    4,
                    TimeUnit.HOURS
            );
            // 恢复订单集合
            for (DeliveryTask task : activeTasks) {
                redisTemplate.opsForSet().add("courier:orders:" + courierId, task.getOrderId().toString());
            }
            redisTemplate.expire("courier:orders:" + courierId, 4, TimeUnit.HOURS);
        }

        // 如果是空闲状态，加入可分配列表
        if (STATUS_IDLE.equals(status) && orderCount < 5) {
            redisTemplate.opsForSet().add(AVAILABLE_COURIERS_KEY, courierId.toString());
        }

        log.info("配送员状态已恢复到Redis, courierId: {}", courierId);
    }
}
