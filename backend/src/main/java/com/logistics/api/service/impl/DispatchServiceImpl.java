package com.logistics.api.service.impl;

import com.logistics.api.common.BusinessException;
import com.logistics.api.common.ErrorCode;
import com.logistics.api.config.DispatchConfig;
import com.logistics.api.enums.OrderStatus;
import com.logistics.api.mapper.*;
import com.logistics.api.messaging.MQMessageSender;
import com.logistics.api.model.*;
import com.logistics.api.service.CourierStatusService;
import com.logistics.api.service.DispatchService;
import com.logistics.api.service.GrabOrderService;
import com.logistics.api.service.LogisticsEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DispatchServiceImpl implements DispatchService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DeliveryTaskMapper deliveryTaskMapper;

    @Autowired
    private OrderAddressMapper orderAddressMapper;

    @Autowired
    private CourierStatusService courierStatusService;

    @Autowired
    private MQMessageSender mqMessageSender;

    @Autowired
    private LogisticsEventService logisticsEventService;

    @Autowired
    private DispatchConfig dispatchConfig;

    @Autowired
    private GrabOrderService grabOrderService;

    private static final double EARTH_RADIUS = 6371.0;

    /**
     * 与 {@link com.logistics.api.service.impl.CourierOrderServiceImpl#getCurrentTasks} 一致：在送中的任务，用于顺路性估算。
     */
    private static final List<String> ACTIVE_ROUTE_STATUSES = List.of(
            OrderStatus.AWAITING_COURIER_CONFIRM.getCode(),
            OrderStatus.AWAITING_PICKUP.getCode(),
            OrderStatus.PICKED_UP.getCode(),
            OrderStatus.IN_TRANSIT.getCode()
    );

    @Override
    @Transactional
    public User autoAssignCourier(Long orderId) {
        log.info("自动分配配送员, orderId: {}", orderId);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        List<OrderAddress> addresses = orderAddressMapper.selectByOrderId(orderId);
        OrderAddress senderAddress = addresses.stream()
                .filter(a -> "sender".equals(a.getType()))
                .findFirst()
                .orElse(null);

        if (senderAddress == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单地址信息不完整");
        }

        String city = senderAddress.getCity();
        Double senderLat = senderAddress.getLatitude() != null ? senderAddress.getLatitude().doubleValue() : null;
        Double senderLng = senderAddress.getLongitude() != null ? senderAddress.getLongitude().doubleValue() : null;

        List<Long> availableCourierIds = findAvailableCouriers(city, senderLat, senderLng);

        if (availableCourierIds.isEmpty()) {
            log.warn("暂无可用配送员, orderId: {}, 订单进入待分配池等待管理员处理", orderId);
            return null;
        }

        int maxOrders = dispatchConfig.getRules().getMaxOrdersPerCourier();
        List<Long> canAcceptIds = availableCourierIds.stream()
                .filter(id -> courierStatusService.canAcceptMoreOrders(id, maxOrders))
                .collect(Collectors.toList());

        if (canAcceptIds.isEmpty()) {
            log.warn("所有配送员已满单, orderId: {}, 订单进入待分配池等待管理员处理", orderId);
            return null;
        }

        List<User> availableCouriers = canAcceptIds.stream()
                .map(userMapper::selectById)
                .filter(user -> user != null)
                .collect(Collectors.toList());

        User selectedCourier = selectBestCourier(availableCouriers, senderLat, senderLng);

        if (selectedCourier == null) {
            log.warn("无法选择合适配送员, orderId: {}, 订单进入待分配池等待管理员处理", orderId);
            return null;
        }

        doAssignCourier(orderId, selectedCourier.getId(), "auto");

        sendDispatchNotification(orderId, selectedCourier.getId());

        log.info("自动分配完成, orderId: {}, courierId: {}, courierName: {}",
                orderId, selectedCourier.getId(), selectedCourier.getName());
        return selectedCourier;
    }

    private List<Long> findAvailableCouriers(String city, Double senderLat, Double senderLng) {
        double searchRadius = dispatchConfig.getRules().getSearchRadiusKm();
        List<Long> courierIds;

        if (senderLat != null && senderLng != null) {
            log.info("使用{}公里范围查找配送员, center: {}, {}", searchRadius, senderLat, senderLng);
            // 优先使用 Redis Geo（按城市），Geo 数据与位置数据同步过期，避免不一致
            courierIds = courierStatusService.getAvailableCourierIdsWithinRadiusByCity(city, senderLat, senderLng, searchRadius);

            if (courierIds.isEmpty() && dispatchConfig.getRules().isAutoExpand()) {
                double expandRadius = dispatchConfig.getRules().getExpandRadiusKm();
                log.info("{}公里范围内暂无可用配送员, 扩大至{}公里", searchRadius, expandRadius);
                courierIds = courierStatusService.getAvailableCourierIdsWithinRadiusByCity(city, senderLat, senderLng, expandRadius);
            }

            if (courierIds.isEmpty()) {
                log.info("按城市查找配送员, city: {}", city);
                courierIds = courierStatusService.getAvailableCourierIds(city);
            }
        } else {
            log.info("按城市查找配送员, city: {}", city);
            courierIds = courierStatusService.getAvailableCourierIds(city);
        }

        return courierIds;
    }

    private void sendDispatchNotification(Long orderId, Long courierId) {
        try {
            mqMessageSender.sendOrderAssignedNotification(orderId, courierId);
        } catch (Exception e) {
            log.error("发送订单分配通知失败, orderId: {}, courierId: {}", orderId, courierId, e);
        }
    }



    @Transactional
    public void doAssignCourier(Long orderId, Long courierId, String dispatchType) {
        log.info("分配配送员, orderId: {}, courierId: {}, dispatchType: {}", orderId, courierId, dispatchType);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        String orderStatus = order.getStatus();
        if (!OrderStatus.PAID.getCode().equals(orderStatus) && 
            !OrderStatus.PENDING.getCode().equals(orderStatus)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单状态不允许分配配送员");
        }

        User courier = userMapper.selectById(courierId);
        if (courier == null) {
            throw new BusinessException(ErrorCode.COURIER_NOT_FOUND);
        }

        String role = courier.getRole();
        if (!"delivery".equals(role)) {
            throw new BusinessException(ErrorCode.COURIER_NOT_FOUND, "指定用户不是配送员");
        }

        List<DeliveryTask> existingTasks = deliveryTaskMapper.selectAllByOrderId(orderId);
        for (DeliveryTask task : existingTasks) {
            if (!"rejected".equals(task.getStatus())) {
                throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单已分配配送员");
            }
        }

        DeliveryTask task = new DeliveryTask();
        task.setOrderId(orderId);
        task.setCourierId(courierId);
        task.setStatus(OrderStatus.AWAITING_COURIER_CONFIRM.getCode());
        deliveryTaskMapper.insert(task);

        orderMapper.updateStatus(orderId, OrderStatus.AWAITING_COURIER_CONFIRM.getCode());
        orderMapper.updateDispatch(orderId, courierId, dispatchType);

        logisticsEventService.recordEvent(orderId, OrderStatus.AWAITING_COURIER_CONFIRM.getCode(), "订单已分配配送员，等待确认", null);

        // 从抢单池中移除订单
        grabOrderService.removeFromGrabPool(orderId);

        log.info("配送员分配完成, orderId: {}, courierId: {}, dispatchType: {}", orderId, courierId, dispatchType);
    }

    @Override
    public List<User> getAvailableCouriers(String city) {
        log.info("获取可用配送员, city: {}", city);

        return userMapper.selectByRole("delivery").stream()
                .filter(courier -> courierStatusService.isAvailableForDispatch(courier.getId()))
                .collect(Collectors.toList());
    }



    @Override
    @Transactional
    public void dispatchOrder(Long orderId, Long courierId) {
        log.info("订单发货并分配, orderId: {}", orderId);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        String orderStatus = order.getStatus();
        if (!OrderStatus.PAID.getCode().equals(orderStatus) && 
            !OrderStatus.PENDING.getCode().equals(orderStatus)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单状态不允许发货");
        }

        User assigned = autoAssignCourier(orderId);
        if (assigned == null) {
            log.info("自动分配失败, 订单进入待分配池, orderId: {}", orderId);
        }

        // 分配成功时 doAssignCourier 已写入 awaiting_courier_confirm 物流节点，勿再插一条 paid，否则用户端会出现多条「已支付」

        log.info("订单发货完成, orderId: {}", orderId);
    }



    private User selectBestCourier(List<User> couriers, Double senderLat, Double senderLng) {
        if (couriers.isEmpty()) {
            return null;
        }

        if (senderLat == null || senderLng == null) {
            log.info("无位置信息, 选择订单最少的配送员");
            return couriers.stream()
                    .min(Comparator.comparingInt(c -> courierStatusService.getCurrentOrderCount(c.getId())))
                    .orElse(null);
        }

        DispatchConfig.RulesConfig rules = dispatchConfig.getRules();
        double routeW = rules.getRoutePenaltyWeight();
        double directW = rules.getDirectDistanceWeight();
        double loadW = rules.getLoadPenaltyKmPerOrder();
        int maxK = rules.getMaxCandidatesForDetourScoring();

        final double sendLat = senderLat;
        final double sendLng = senderLng;

        List<CourierCandidate> sorted = couriers.stream()
                .map(c -> buildCourierCandidate(c, sendLat, sendLng))
                .sorted(Comparator.comparingDouble(cc -> cc.directKm))
                .collect(Collectors.toList());

        List<CourierCandidate> toScore;
        if (maxK > 0 && sorted.size() > maxK) {
            toScore = new java.util.ArrayList<>(sorted.subList(0, maxK));
            log.debug("派单顺路精算 Top-{} / 候选 {} 人", maxK, sorted.size());
        } else {
            toScore = sorted;
        }

        List<Long> courierIds = toScore.stream().map(cc -> cc.courier.getId()).collect(Collectors.toList());
        List<DeliveryTask> batchTasks = courierIds.isEmpty()
                ? List.of()
                : deliveryTaskMapper.selectByCourierIdsAndStatuses(courierIds, ACTIVE_ROUTE_STATUSES);
        Map<Long, List<DeliveryTask>> tasksByCourier = batchTasks.stream()
                .collect(Collectors.groupingBy(DeliveryTask::getCourierId));

        List<Long> orderIds = batchTasks.stream().map(DeliveryTask::getOrderId).distinct().collect(Collectors.toList());
        Map<Long, Order> orderById = new java.util.HashMap<>();
        if (!orderIds.isEmpty()) {
            for (Order o : orderMapper.selectByIds(orderIds)) {
                orderById.put(o.getId(), o);
            }
        }
        Map<Long, Map<String, OrderAddress>> addrByOrderAndType = new java.util.HashMap<>();
        if (!orderIds.isEmpty()) {
            for (OrderAddress a : orderAddressMapper.selectByOrderIds(orderIds)) {
                addrByOrderAndType.computeIfAbsent(a.getOrderId(), k -> new java.util.HashMap<>()).put(a.getType(), a);
            }
        }

        User best = null;
        double bestScore = Double.MAX_VALUE;
        for (CourierCandidate cc : toScore) {
            if (cc.directKm == Double.MAX_VALUE) {
                continue;
            }
            int orderCount = courierStatusService.getCurrentOrderCount(cc.courier.getId());
            double detourKm = computeRouteDetourKmCached(cc, sendLat, sendLng, tasksByCourier, orderById, addrByOrderAndType);
            double score = routeW * detourKm + directW * cc.directKm + loadW * orderCount;
            log.debug("自动派单评分 courierId={} detourKm={} directKm={} orders={} score={}",
                    cc.courier.getId(), String.format("%.3f", detourKm), String.format("%.3f", cc.directKm),
                    orderCount, String.format("%.3f", score));
            if (score < bestScore) {
                bestScore = score;
                best = cc.courier;
            }
        }

        if (best != null) {
            return best;
        }
        log.info("候选配送员均无有效位置，按在送单数最少指派");
        return sorted.stream()
                .min(Comparator.comparingInt(cc -> courierStatusService.getCurrentOrderCount(cc.courier.getId())))
                .map(cc -> cc.courier)
                .orElse(null);
    }

    private CourierCandidate buildCourierCandidate(User courier, double senderLat, double senderLng) {
        String locationStr = courierStatusService.getCourierLocation(courier.getId());
        if (locationStr == null) {
            return new CourierCandidate(courier, Double.MAX_VALUE, false, 0, 0);
        }
        try {
            String[] parts = locationStr.split(",");
            double courierLat = Double.parseDouble(parts[0]);
            double courierLng = Double.parseDouble(parts[1]);
            double directKm = calculateDistance(courierLat, courierLng, senderLat, senderLng);
            return new CourierCandidate(courier, directKm, true, courierLat, courierLng);
        } catch (Exception e) {
            log.error("解析配送员位置失败, courierId: {}", courier.getId(), e);
            return new CourierCandidate(courier, Double.MAX_VALUE, false, 0, 0);
        }
    }

    /**
     * 顺路性（简化）：对在送订单取「下一关键节点」——未揽件前为寄件坐标，揽件后为收件坐标。
     * 额外里程 = max(0, dist(配送员,新寄件)+dist(新寄件,节点) - dist(配送员,节点))，多单取最小（最顺路的一条解释）。
     * 手里没有在送单时返回 0。数据由外层批量查询传入，避免每人多次 DB。
     */
    private double computeRouteDetourKmCached(CourierCandidate cc, double newSenderLat, double newSenderLng,
                                              Map<Long, List<DeliveryTask>> tasksByCourier,
                                              Map<Long, Order> orderById,
                                              Map<Long, Map<String, OrderAddress>> addrByOrderAndType) {
        if (!cc.hasLatLng) {
            return 0.0;
        }
        List<DeliveryTask> tasks = tasksByCourier.getOrDefault(cc.courier.getId(), List.of());
        if (tasks.isEmpty()) {
            return 0.0;
        }
        double courierLat = cc.courierLat;
        double courierLng = cc.courierLng;

        double minDetour = Double.MAX_VALUE;
        boolean anyAnchor = false;
        for (DeliveryTask task : tasks) {
            Order order = orderById.get(task.getOrderId());
            if (order == null) {
                continue;
            }
            String addrType = isHeadingToPickup(order.getStatus()) ? "sender" : "receiver";
            Map<String, OrderAddress> amap = addrByOrderAndType.get(task.getOrderId());
            if (amap == null) {
                continue;
            }
            OrderAddress anchorAddr = amap.get(addrType);
            if (anchorAddr == null || anchorAddr.getLatitude() == null || anchorAddr.getLongitude() == null) {
                continue;
            }
            double ax = anchorAddr.getLatitude().doubleValue();
            double ay = anchorAddr.getLongitude().doubleValue();
            anyAnchor = true;
            double directToAnchor = calculateDistance(courierLat, courierLng, ax, ay);
            double viaNewSender = calculateDistance(courierLat, courierLng, newSenderLat, newSenderLng)
                    + calculateDistance(newSenderLat, newSenderLng, ax, ay);
            double detour = Math.max(0.0, viaNewSender - directToAnchor);
            minDetour = Math.min(minDetour, detour);
        }

        if (!anyAnchor) {
            return 0.0;
        }
        return minDetour;
    }

    /** 尚未完成揽件前，下一停靠点视为寄件地；之后视为收件地 */
    private boolean isHeadingToPickup(String orderStatus) {
        if (orderStatus == null) {
            return true;
        }
        return OrderStatus.AWAITING_COURIER_CONFIRM.getCode().equals(orderStatus)
                || OrderStatus.AWAITING_PICKUP.getCode().equals(orderStatus);
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

    @Override
    @Transactional
    public void confirmOrderBy(Long orderId, Long courierId) {
        log.info("配送员确认订单, orderId: {}, courierId: {}", orderId, courierId);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        if (!OrderStatus.AWAITING_COURIER_CONFIRM.getCode().equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单状态不允许确认");
        }

        DeliveryTask task = deliveryTaskMapper.selectByCourierIdAndOrderId(courierId, orderId);
        if (task == null || "rejected".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "无权确认此订单");
        }

        orderMapper.updateStatus(orderId, OrderStatus.AWAITING_PICKUP.getCode());
        deliveryTaskMapper.updateStatus(task.getId(), OrderStatus.AWAITING_PICKUP.getCode());
        deliveryTaskMapper.updateAcceptTime(task.getId());

        logisticsEventService.recordEvent(orderId, OrderStatus.AWAITING_PICKUP.getCode(),
            "配送员已接单，等待揽件", courierId);

        log.info("配送员确认订单成功, orderId: {}, courierId: {}", orderId, courierId);
    }

    @Override
    @Transactional
    public void rejectOrder(Long orderId, Long courierId, String reason) {
        log.info("配送员拒绝订单, orderId: {}, courierId: {}, reason: {}", orderId, courierId, reason);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        if (!OrderStatus.AWAITING_COURIER_CONFIRM.getCode().equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单状态不允许拒绝");
        }

        DeliveryTask task = deliveryTaskMapper.selectByCourierIdAndOrderId(courierId, orderId);
        if (task == null || "rejected".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "无权拒绝此订单");
        }

        deliveryTaskMapper.updateStatus(task.getId(), "rejected");
        orderMapper.updateStatus(orderId, OrderStatus.PAID.getCode());
        orderMapper.clearCourier(orderId);

        courierStatusService.finishDelivery(courierId, orderId);

        logisticsEventService.recordEvent(orderId, "rejected", 
            "配送员拒绝接单, 原因: " + (reason != null ? reason : "无"), courierId);

        grabOrderService.addToGrabPool(orderId);

        log.info("配送员拒绝订单成功, 订单已加入抢单池, orderId: {}", orderId);
    }

    /** 自动派单候选：预计算到本单寄件点距离，供 Top-K 与顺路批量打分 */
    private static final class CourierCandidate {
        final User courier;
        final double directKm;
        final boolean hasLatLng;
        final double courierLat;
        final double courierLng;

        CourierCandidate(User courier, double directKm, boolean hasLatLng, double courierLat, double courierLng) {
            this.courier = courier;
            this.directKm = directKm;
            this.hasLatLng = hasLatLng;
            this.courierLat = courierLat;
            this.courierLng = courierLng;
        }
    }
}
