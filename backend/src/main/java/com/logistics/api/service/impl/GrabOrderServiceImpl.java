package com.logistics.api.service.impl;

import com.logistics.api.dto.OrderDTO;
import com.logistics.api.enums.OrderStatus;
import com.logistics.api.mapper.DeliveryTaskMapper;
import com.logistics.api.mapper.OrderAddressMapper;
import com.logistics.api.mapper.OrderMapper;
import com.logistics.api.mapper.UserMapper;
import com.logistics.api.model.DeliveryTask;
import com.logistics.api.model.Order;
import com.logistics.api.model.OrderAddress;
import com.logistics.api.model.User;
import com.logistics.api.service.CourierStatusService;
import com.logistics.api.service.GrabOrderService;
import com.logistics.api.service.LogisticsEventService;
import com.logistics.api.service.NotificationService;
import com.logistics.api.service.support.NotificationCopy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class GrabOrderServiceImpl implements GrabOrderService {

    // 抢单池key前缀: orders:grab_pool:{city}:{district}
    // 例如: orders:grab_pool:北京市:朝阳区
    private static final String GRAB_POOL_KEY_PREFIX = "orders:grab_pool:";
    private static final String GRAB_LOCK_PREFIX = "orders:grab_lock:";
    private static final double EARTH_RADIUS = 6371.0;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderAddressMapper orderAddressMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DeliveryTaskMapper deliveryTaskMapper;

    @Autowired
    private CourierStatusService courierStatusService;

    @Autowired
    private LogisticsEventService logisticsEventService;

    @Autowired
    private NotificationService notificationService;

    /**
     * 统一城市名称格式：去掉"市"、"省"等后缀，避免"上海"和"上海市"不匹配
     */
    private String normalizeCityName(String city) {
        if (city == null || city.isEmpty()) {
            return city;
        }
        String normalized = city.trim();
        String[] suffixes = {"市", "省", "自治区", "特别行政区"};
        for (String suffix : suffixes) {
            if (normalized.endsWith(suffix)) {
                normalized = normalized.substring(0, normalized.length() - suffix.length());
                break;
            }
        }
        return normalized;
    }

    /**
     * 构建抢单池Redis key
     * 格式: orders:grab_pool:{city}:{district}
     * 如果district为空，则使用 orders:grab_pool:{city}:default
     * 使用统一后的城市名，确保一致性
     */
    private String buildGrabPoolKey(String city, String district) {
        if (city == null || city.isEmpty()) {
            city = "unknown";
        }
        if (district == null || district.isEmpty()) {
            district = "default";
        }
        // 使用统一后的城市名，避免"上海"和"上海市"不一致
        return GRAB_POOL_KEY_PREFIX + normalizeCityName(city) + ":" + district;
    }

    /**
     * 从订单地址中提取寄件地址的区域信息
     */
    private String[] extractRegionFromOrder(Long orderId) {
        List<OrderAddress> addresses = orderAddressMapper.selectByOrderId(orderId);
        for (OrderAddress addr : addresses) {
            if ("sender".equals(addr.getType())) {
                String city = addr.getCity() != null ? addr.getCity() : "unknown";
                String district = addr.getDistrict() != null ? addr.getDistrict() : "default";
                return new String[]{city, district};
            }
        }
        return new String[]{"unknown", "default"};
    }

    @Override
    public void addToGrabPool(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("订单不存在, orderId: {}", orderId);
            return;
        }

        if (order.getCourierId() != null) {
            log.info("订单已分配配送员, 不加入抢单池, orderId: {}, courierId: {}", orderId, order.getCourierId());
            return;
        }

        // 根据寄件地址区域构建key
        String[] region = extractRegionFromOrder(orderId);
        String poolKey = buildGrabPoolKey(region[0], region[1]);
        
        // 检查是否已有配送任务（拒绝状态的不算）
        List<DeliveryTask> existingTasks = deliveryTaskMapper.selectAllByOrderId(orderId);
        for (DeliveryTask task : existingTasks) {
            if (!"rejected".equals(task.getStatus())) {
                log.warn("订单已有配送任务, 不加入抢单池, orderId: {}, taskId: {}", orderId, task.getId());
                return;
            }
        }
        
        redisTemplate.opsForSet().add(poolKey, orderId.toString());
        log.info("订单加入抢单池, orderId: {}, poolKey: {}, orderStatus: {}, courierId: {}", 
            orderId, poolKey, order.getStatus(), order.getCourierId());
    }

    @Override
    public void removeFromGrabPool(Long orderId) {
        // 由于不知道订单在哪个区域的池中，需要尝试从所有可能的池中移除
        // 先根据订单地址获取区域
        String[] region = extractRegionFromOrder(orderId);
        String poolKey = buildGrabPoolKey(region[0], region[1]);
        
        redisTemplate.opsForSet().remove(poolKey, orderId.toString());
        redisTemplate.delete(GRAB_LOCK_PREFIX + orderId);
        log.info("订单从抢单池移除, orderId: {}, poolKey: {}", orderId, poolKey);
    }

    @Override
    public List<OrderDTO> getGrabableOrders(String city, Double lat, Double lng, int limit) {
        // 如果没有提供城市信息，无法查询
        if (city == null || city.isEmpty()) {
            log.warn("查询抢单池时未提供城市信息");
            return Collections.emptyList();
        }

        // 统一城市名称格式，支持多种格式匹配
        String normalizedCity = normalizeCityName(city);
        
        // 构建该城市下所有区的key模式（尝试原始城市和统一后的城市）
        Set<String> poolKeys = new HashSet<>();
        
        // 尝试原始城市名
        String cityKeyPattern1 = GRAB_POOL_KEY_PREFIX + city + ":*";
        Set<String> keys1 = redisTemplate.keys(cityKeyPattern1);
        if (keys1 != null) {
            poolKeys.addAll(keys1);
        }
        
        // 尝试统一后的城市名（如果不同）
        if (!city.equals(normalizedCity)) {
            String cityKeyPattern2 = GRAB_POOL_KEY_PREFIX + normalizedCity + ":*";
            Set<String> keys2 = redisTemplate.keys(cityKeyPattern2);
            if (keys2 != null) {
                poolKeys.addAll(keys2);
            }
        }
        
        if (poolKeys.isEmpty()) {
            log.debug("该城市暂无抢单池, city: {} (normalized: {})", city, normalizedCity);
            return Collections.emptyList();
        }

        List<OrderDTO> result = new ArrayList<>();
        
        // 遍历所有区的抢单池
        for (String poolKey : poolKeys) {
            if (result.size() >= limit) {
                break;
            }

            Set<String> orderIds = redisTemplate.opsForSet().members(poolKey);
            if (orderIds == null || orderIds.isEmpty()) {
                continue;
            }

            for (String orderIdStr : orderIds) {
                if (result.size() >= limit) {
                    break;
                }

                Long orderId = Long.valueOf(orderIdStr);
                Order order = orderMapper.selectById(orderId);
                
                if (order == null) {
                    log.warn("从抢单池移除无效订单: 订单不存在, orderId: {}, poolKey: {}", orderId, poolKey);
                    redisTemplate.opsForSet().remove(poolKey, orderIdStr);
                    continue;
                }
                
                if (order.getCourierId() != null) {
                    log.warn("从抢单池移除已分配订单: orderId: {}, courierId: {}, poolKey: {}", 
                        orderId, order.getCourierId(), poolKey);
                    redisTemplate.opsForSet().remove(poolKey, orderIdStr);
                    continue;
                }

                if (!OrderStatus.PENDING.getCode().equals(order.getStatus()) &&
                    !OrderStatus.PAID.getCode().equals(order.getStatus())) {
                    log.warn("从抢单池移除状态异常订单: orderId: {}, status: {}, poolKey: {}", 
                        orderId, order.getStatus(), poolKey);
                    redisTemplate.opsForSet().remove(poolKey, orderIdStr);
                    continue;
                }

                List<DeliveryTask> existingTasks = deliveryTaskMapper.selectAllByOrderId(orderId);
                boolean hasValidTask = false;
                for (DeliveryTask task : existingTasks) {
                    if (!"rejected".equals(task.getStatus())) {
                        hasValidTask = true;
                        break;
                    }
                }
                if (hasValidTask) {
                    log.warn("从抢单池移除已有配送任务的订单: orderId: {}, poolKey: {}", orderId, poolKey);
                    redisTemplate.opsForSet().remove(poolKey, orderIdStr);
                    continue;
                }

                OrderDTO dto = convertToDTO(order);

                List<OrderAddress> addresses = orderAddressMapper.selectByOrderId(orderId);
                for (OrderAddress addr : addresses) {
                    if ("sender".equals(addr.getType())) {
                        dto.setSenderName(addr.getContactName());
                        dto.setSenderPhone(addr.getContactPhone());
                        dto.setSenderAddress(formatAddress(addr));
                        dto.setSenderLatitude(addr.getLatitude());
                        dto.setSenderLongitude(addr.getLongitude());
                        
                        // 计算距离
                        if (lat != null && lng != null && addr.getLatitude() != null && addr.getLongitude() != null) {
                            double distance = calculateDistance(lat, lng, 
                                    addr.getLatitude().doubleValue(), addr.getLongitude().doubleValue());
                            dto.setDistance(Math.round(distance * 100) / 100.0);
                        }
                    } else {
                        dto.setReceiverName(addr.getContactName());
                        dto.setReceiverPhone(addr.getContactPhone());
                        dto.setReceiverAddress(formatAddress(addr));
                    }
                }

                result.add(dto);
            }
        }

        // 按距离排序（近的在前）
        result.sort((a, b) -> {
            Double distA = a.getDistance();
            Double distB = b.getDistance();
            if (distA == null) return 1;
            if (distB == null) return -1;
            return Double.compare(distA, distB);
        });

        return result;
    }

    @Override
    @Transactional
    public String tryGrabOrder(Long orderId, Long courierId) {
        log.info("配送员抢单, orderId: {}, courierId: {}", orderId, courierId);

        String lockKey = GRAB_LOCK_PREFIX + orderId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, courierId.toString(), 30, TimeUnit.SECONDS);
        
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("抢单失败, 订单已被锁定, orderId: {}, courierId: {}", orderId, courierId);
            return "订单正在被其他骑手抢单，请稍后再试";
        }

        try {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                log.warn("抢单失败, 订单不存在, orderId: {}", orderId);
                return "该订单已不存在，请刷新列表";
            }

            if (order.getCourierId() != null) {
                log.warn("抢单失败, 订单已被分配, orderId: {}, existingCourierId: {}", orderId, order.getCourierId());
                return "该订单已被其他骑手接单，请刷新列表";
            }

            if (!OrderStatus.PENDING.getCode().equals(order.getStatus()) &&
                !OrderStatus.PAID.getCode().equals(order.getStatus())) {
                log.warn("抢单失败, 订单状态不允许, orderId: {}, status: {}", orderId, order.getStatus());
                if (OrderStatus.AWAITING_PICKUP.getCode().equals(order.getStatus())) {
                    return "该订单已被接单，正在等待揽件";
                } else if (OrderStatus.PICKED_UP.getCode().equals(order.getStatus())) {
                    return "该订单正在配送中，无法抢单";
                } else if (OrderStatus.IN_TRANSIT.getCode().equals(order.getStatus())) {
                    return "该订单正在运输中，无法抢单";
                } else if (OrderStatus.DELIVERED.getCode().equals(order.getStatus())) {
                    return "该订单已完成配送";
                } else if (OrderStatus.CANCELLED.getCode().equals(order.getStatus())) {
                    return "该订单已取消";
                }
                return "该订单当前状态无法抢单";
            }

            User courier = userMapper.selectById(courierId);
            if (courier == null || !"delivery".equals(courier.getRole())) {
                log.warn("抢单失败, 配送员无效, courierId: {}", courierId);
                return "您的账号状态异常，请联系客服";
            }

            List<DeliveryTask> existingTasks = deliveryTaskMapper.selectAllByOrderId(orderId);
            boolean hasValidTask = false;
            for (DeliveryTask task : existingTasks) {
                if (!"rejected".equals(task.getStatus())) {
                    hasValidTask = true;
                    break;
                }
            }
            if (hasValidTask) {
                log.warn("抢单失败, 订单已有配送任务, orderId: {}", orderId);
                return "该订单已被其他骑手接单，请刷新列表";
            }

            DeliveryTask task = new DeliveryTask();
            task.setOrderId(orderId);
            task.setCourierId(courierId);
            task.setStatus(OrderStatus.AWAITING_PICKUP.getCode());
            deliveryTaskMapper.insert(task);

            orderMapper.updateStatus(orderId, OrderStatus.AWAITING_PICKUP.getCode());
            orderMapper.updateDispatch(orderId, courierId, "grab");

            logisticsEventService.recordEvent(orderId, OrderStatus.AWAITING_PICKUP.getCode(),
                "配送员已接单，等待揽件", courierId);

            // 仅通知下单用户：骑手抢单成功已在操作界面即时反馈，不再写入骑手端消息中心以免重复打扰
            notificationService.sendOrderNotification(
                    order.getUserId(),
                    "user",
                    NotificationCopy.grabOrderUserTitle(),
                    NotificationCopy.grabOrderUserBody(order.getOrderNo(), courier.getName()),
                    orderId
            );

            // 抢单成功仅增加订单计数，不改变 idle 状态（仍可继续接单）
            courierStatusService.addOrderToCourier(courierId, orderId);

            removeFromGrabPool(orderId);

            log.info("抢单成功, orderId: {}, courierId: {}, courierName: {}", orderId, courierId, courier.getName());
            return null;

        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    public boolean isInGrabPool(Long orderId) {
        // 根据订单地址获取区域
        String[] region = extractRegionFromOrder(orderId);
        String poolKey = buildGrabPoolKey(region[0], region[1]);
        
        Boolean isMember = redisTemplate.opsForSet().isMember(poolKey, orderId.toString());
        return Boolean.TRUE.equals(isMember);
    }

    @Override
    public int getGrabPoolSize() {
        // 获取所有抢单池key
        Set<String> poolKeys = redisTemplate.keys(GRAB_POOL_KEY_PREFIX + "*");
        if (poolKeys == null || poolKeys.isEmpty()) {
            return 0;
        }

        int totalSize = 0;
        for (String poolKey : poolKeys) {
            Long size = redisTemplate.opsForSet().size(poolKey);
            if (size != null) {
                totalSize += size.intValue();
            }
        }
        return totalSize;
    }

    /**
     * 获取指定区域的抢单池大小
     */
    public int getGrabPoolSize(String city, String district) {
        String poolKey = buildGrabPoolKey(city, district);
        Long size = redisTemplate.opsForSet().size(poolKey);
        return size != null ? size.intValue() : 0;
    }

    @Override
    public List<Long> getAllGrabPoolOrderIds() {
        Set<String> poolKeys = redisTemplate.keys(GRAB_POOL_KEY_PREFIX + "*");
        if (poolKeys == null || poolKeys.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> allOrderIds = new ArrayList<>();
        for (String poolKey : poolKeys) {
            Set<String> orderIds = redisTemplate.opsForSet().members(poolKey);
            if (orderIds != null) {
                for (String orderIdStr : orderIds) {
                    try {
                        allOrderIds.add(Long.valueOf(orderIdStr));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid orderId in grab pool: {}", orderIdStr);
                    }
                }
            }
        }
        return allOrderIds;
    }

    @Override
    public List<OrderDTO> getPaidOrdersNotInGrabPool() {
        // 1. 获取所有抢单池中的订单ID（批量从Redis获取）
        List<Long> grabPoolOrderIds = getAllGrabPoolOrderIds();
        
        // 2. 查询数据库中状态为 PAID 且未分配配送员的订单
        List<Order> paidOrders = orderMapper.selectByStatus(OrderStatus.PAID.getCode());
        
        // 3. 将抢单池订单ID放入 HashSet，实现 O(1) 查找
        Set<Long> grabPoolSet = new HashSet<>(grabPoolOrderIds);
        
        // 4. 过滤出不在抢单池中的订单
        List<OrderDTO> result = new ArrayList<>();
        for (Order order : paidOrders) {
            // 只处理未分配配送员的订单
            if (order.getCourierId() == null && !grabPoolSet.contains(order.getId())) {
                OrderDTO dto = convertToDTO(order);
                
                // 补充地址信息
                List<OrderAddress> addresses = orderAddressMapper.selectByOrderId(order.getId());
                for (OrderAddress addr : addresses) {
                    if ("sender".equals(addr.getType())) {
                        dto.setSenderName(addr.getContactName());
                        dto.setSenderPhone(addr.getContactPhone());
                        dto.setSenderAddress(formatAddress(addr));
                        dto.setSenderLatitude(addr.getLatitude());
                        dto.setSenderLongitude(addr.getLongitude());
                    } else {
                        dto.setReceiverName(addr.getContactName());
                        dto.setReceiverPhone(addr.getContactPhone());
                        dto.setReceiverAddress(formatAddress(addr));
                    }
                }
                
                result.add(dto);
            }
        }
        
        log.info("查询到 {} 个已支付但未在抢单池的订单", result.size());
        return result;
    }

    @Override
    public int batchAddToGrabPool(List<Long> orderIds) {
        int successCount = 0;
        for (Long orderId : orderIds) {
            try {
                addToGrabPool(orderId);
                successCount++;
            } catch (Exception e) {
                log.error("批量加入抢单池失败, orderId: {}", orderId, e);
            }
        }
        return successCount;
    }

    private OrderDTO convertToDTO(Order order) {
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setGoodsTypeId(order.getGoodsTypeId());
        dto.setGoodsDescription(order.getGoodsDescription());
        dto.setGoodsWeight(order.getGoodsWeight());
        dto.setStatus(order.getStatus());
        dto.setOrderType(order.getOrderType());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setCreateTime(order.getCreateTime());
        return dto;
    }

    private String formatAddress(OrderAddress addr) {
        StringBuilder sb = new StringBuilder();
        if (addr.getProvince() != null) sb.append(addr.getProvince());
        if (addr.getCity() != null) sb.append(addr.getCity());
        if (addr.getDistrict() != null) sb.append(addr.getDistrict());
        if (addr.getDetailAddress() != null) sb.append(addr.getDetailAddress());
        return sb.toString();
    }

    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }
}
