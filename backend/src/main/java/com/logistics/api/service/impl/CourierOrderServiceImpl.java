package com.logistics.api.service.impl;

import com.logistics.api.common.BusinessException;
import com.logistics.api.common.ErrorCode;
import com.logistics.api.dto.CourierTaskDTO;
import com.logistics.api.enums.OrderExceptionType;
import com.logistics.api.enums.OrderStatus;
import com.logistics.api.mapper.*;
import com.logistics.api.model.*;
import com.logistics.api.service.CourierOrderService;
import com.logistics.api.service.CourierStatusService;
import com.logistics.api.service.NotificationService;
import com.logistics.api.service.TrackingService;
import com.logistics.api.service.WalletService;
import com.logistics.api.service.support.NotificationCopy;
import com.logistics.api.service.support.OrderStatusValidator;
import com.logistics.api.websocket.TrackingWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CourierOrderServiceImpl implements CourierOrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private DeliveryTaskMapper deliveryTaskMapper;

    @Autowired
    private LogisticsEventMapper logisticsEventMapper;

    @Autowired
    private CourierStatusService courierStatusService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private OrderStatusValidator orderStatusValidator;

    @Autowired
    @Lazy
    private TrackingWebSocketHandler trackingWebSocketHandler;

    @Value("${app.test-mode:false}")
    private boolean testMode;

    private Order requireCourierTask(Long orderId, Long courierId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        DeliveryTask task = deliveryTaskMapper.selectByCourierIdAndOrderId(courierId, orderId);
        if (task == null || "rejected".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此订单");
        }
        return order;
    }

    private void insertEvent(Long orderId, String status, String description, String location,
                            BigDecimal latitude, BigDecimal longitude, Long operatorId) {
        LogisticsEvent event = new LogisticsEvent();
        event.setOrderId(orderId);
        event.setStatus(status);
        event.setDescription(description);
        event.setLocation(location);
        event.setLatitude(latitude);
        event.setLongitude(longitude);
        event.setOperatorId(operatorId);
        event.setCreateTime(LocalDateTime.now());
        logisticsEventMapper.insert(event);
        if (trackingWebSocketHandler != null) {
            trackingWebSocketHandler.sendLogisticsEventUpdate(orderId, event);
        }
    }

    private String suffixLocation(String location) {
        return location != null && !location.isEmpty() ? "，地点: " + location : "";
    }

    @Override
    @Transactional
    public void pickupOrder(Long orderId, Long courierId, String location, 
                           BigDecimal latitude, BigDecimal longitude) {
        log.info("配送员揽件, orderId: {}, courierId: {}", orderId, courierId);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        String orderStatus = order.getStatus();
        if (!OrderStatus.AWAITING_PICKUP.getCode().equals(orderStatus)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单状态不允许揽件");
        }
        orderStatusValidator.requireTransition(orderStatus, OrderStatus.PICKED_UP.getCode());

        DeliveryTask task = deliveryTaskMapper.selectByCourierIdAndOrderId(courierId, orderId);
        if (task == null || "rejected".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此订单");
        }

        orderMapper.updateStatus(orderId, OrderStatus.PICKED_UP.getCode());
        deliveryTaskMapper.updateStatus(task.getId(), OrderStatus.PICKED_UP.getCode());
        deliveryTaskMapper.updatePickupTime(task.getId());

        insertEvent(orderId, OrderStatus.PICKED_UP.getCode(),
                "快件已揽收",
                location, latitude, longitude, courierId);

        // 发送揽件通知给用户
        notificationService.sendOrderNotification(
                order.getUserId(),
                "user",
                NotificationCopy.pickedUpTitle(),
                NotificationCopy.pickedUpBody(order.getOrderNo()),
                orderId
        );

        log.info("揽件成功, orderId: {}", orderId);
    }

    @Override
    @Transactional
    public void pickupOrderByNo(String orderNo, Long courierId) {
        log.info("根据订单号揽件, orderNo: {}, courierId: {}", orderNo, courierId);

        // 根据订单号查询订单
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在，请检查订单号");
        }

        Long orderId = order.getId();

        // 检查订单状态
        String orderStatus = order.getStatus();
        if (!OrderStatus.AWAITING_PICKUP.getCode().equals(orderStatus)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单状态不允许揽件，当前状态: " + orderStatus);
        }
        orderStatusValidator.requireTransition(orderStatus, OrderStatus.PICKED_UP.getCode());

        // 检查配送任务
        DeliveryTask task = deliveryTaskMapper.selectByCourierIdAndOrderId(courierId, orderId);
        if (task == null || "rejected".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "该订单未分配给您，无权操作");
        }

        // 更新订单状态
        orderMapper.updateStatus(orderId, OrderStatus.PICKED_UP.getCode());
        deliveryTaskMapper.updateStatus(task.getId(), OrderStatus.PICKED_UP.getCode());
        deliveryTaskMapper.updatePickupTime(task.getId());

        insertEvent(orderId, OrderStatus.PICKED_UP.getCode(), "快件已揽收",
                null, null, null, courierId);

        notificationService.sendOrderNotification(
                order.getUserId(),
                "user",
                NotificationCopy.pickedUpTitle(),
                NotificationCopy.pickedUpBody(order.getOrderNo()),
                orderId
        );

        log.info("根据订单号揽件成功, orderNo: {}, orderId: {}", orderNo, orderId);
    }

    @Override
    @Transactional
    public void updateInTransit(Long orderId, Long courierId, String location, String description) {
        log.info("更新运输状态, orderId: {}, courierId: {}", orderId, courierId);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        DeliveryTask task = deliveryTaskMapper.selectByCourierIdAndOrderId(courierId, orderId);
        if (task == null || "rejected".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此订单");
        }

        String orderStatus = order.getStatus();

        if (!OrderStatus.PICKED_UP.getCode().equals(orderStatus)
                && !OrderStatus.IN_TRANSIT.getCode().equals(orderStatus)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单状态不允许更新运输状态");
        }

        if (OrderStatus.PICKED_UP.getCode().equals(orderStatus)) {
            orderStatusValidator.requireTransition(orderStatus, OrderStatus.IN_TRANSIT.getCode());
        }

        orderMapper.updateStatus(orderId, OrderStatus.IN_TRANSIT.getCode());
        if (task != null) {
            deliveryTaskMapper.updateStatus(task.getId(), OrderStatus.IN_TRANSIT.getCode());
        }

        insertEvent(orderId, OrderStatus.IN_TRANSIT.getCode(),
                "快件运输中",
                location, null, null, courierId);

        log.info("运输状态更新成功, orderId: {}", orderId);
    }



    @Override
    @Transactional
    public void startDelivery(Long orderId, Long courierId, String location) {
        log.info("开始运输, orderId: {}, courierId: {}", orderId, courierId);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        DeliveryTask task = deliveryTaskMapper.selectByCourierIdAndOrderId(courierId, orderId);
        if (task == null || "rejected".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此订单");
        }

        String st = order.getStatus();
        if (!OrderStatus.PICKED_UP.getCode().equals(st)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单状态不允许开始运输");
        }
        orderStatusValidator.requireTransition(st, OrderStatus.IN_TRANSIT.getCode());

        orderMapper.updateStatus(orderId, OrderStatus.IN_TRANSIT.getCode());
        deliveryTaskMapper.updateStatus(task.getId(), OrderStatus.IN_TRANSIT.getCode());

        // 同步更新 Redis：配送员开始配送后，状态设为 busy，从可分配列表移除
        courierStatusService.startDelivery(courierId, orderId);

        insertEvent(orderId, OrderStatus.IN_TRANSIT.getCode(),
                "配送员开始运输",
                location, null, null, courierId);

        log.info("开始运输成功, orderId: {}", orderId);
    }

    @Override
    @Transactional
    public void confirmDelivery(Long orderId, Long courierId, String location,
                               BigDecimal latitude, BigDecimal longitude) {
        log.info("确认送达, orderId: {}, courierId: {}", orderId, courierId);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        String deliverySt = order.getStatus();
        if (!OrderStatus.IN_TRANSIT.getCode().equals(deliverySt)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单状态不允许确认送达");
        }
        orderStatusValidator.requireTransition(deliverySt, OrderStatus.DELIVERED.getCode());

        DeliveryTask task = deliveryTaskMapper.selectByCourierIdAndOrderId(courierId, orderId);
        if (task == null || "rejected".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此订单");
        }

        // 检查配送员当前位置与收货地址距离（必须在 5 公里以内）
        // 测试环境跳过距离校验，支持模拟轨迹场景
        if (!testMode) {
            OrderAddress receiverAddress = orderAddressMapper.selectByOrderIdAndType(orderId, "receiver");
            if (receiverAddress != null && receiverAddress.getLatitude() != null && receiverAddress.getLongitude() != null) {
                double courierLat;
                double courierLng;

                // 优先使用接口传入的当前位置，否则从轨迹系统取最新位置
                if (latitude != null && longitude != null) {
                    courierLat = latitude.doubleValue();
                    courierLng = longitude.doubleValue();
                } else {
                    var latest = trackingService.getLatestLocation(orderId);
                    if (latest == null || latest.getLatitude() == null || latest.getLongitude() == null) {
                        throw new BusinessException(ErrorCode.LOCATION_ERROR, "无法获取当前位置，请开启定位后重试");
                    }
                    courierLat = latest.getLatitude().doubleValue();
                    courierLng = latest.getLongitude().doubleValue();
                }

                double distanceKm = calculateDistanceKm(
                        courierLat, courierLng,
                        receiverAddress.getLatitude().doubleValue(),
                        receiverAddress.getLongitude().doubleValue()
                );

                if (distanceKm > 5.0) {
                    throw new BusinessException(ErrorCode.LOCATION_ERROR,
                            "您当前距离收货地址约 " + String.format("%.1f", distanceKm) + " 公里，需在 5 公里以内方可确认送达");
                }
            }
        } else {
            log.info("测试模式：跳过确认送达距离校验, orderId: {}", orderId);
        }

        orderMapper.updateStatus(orderId, OrderStatus.DELIVERED.getCode());
        deliveryTaskMapper.updateStatus(task.getId(), OrderStatus.DELIVERED.getCode());
        deliveryTaskMapper.updateDeliveryTime(task.getId());

        insertEvent(orderId, OrderStatus.DELIVERED.getCode(),
                "快件已签收",
                location, latitude, longitude, courierId);

        courierStatusService.finishDelivery(courierId, orderId);

        // 配送员收入结算：订单金额的 90%
        BigDecimal orderAmount = order.getActualAmount() != null ? order.getActualAmount() : order.getTotalAmount();
        if (orderAmount != null && orderAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal income = orderAmount.multiply(new BigDecimal("0.90"));
            walletService.addIncome(courierId, income, orderId);
            log.info("配送员收入到账: courierId={}, orderId={}, amount={}", courierId, orderId, income);
        }

        // 发送送达通知给收件人（通过收件人手机号查找注册用户）
        OrderAddress receiverAddress = orderAddressMapper.selectByOrderIdAndType(orderId, "receiver");
        if (receiverAddress != null && receiverAddress.getContactPhone() != null) {
            User receiverUser = userMapper.selectByPhone(receiverAddress.getContactPhone());
            if (receiverUser != null) {
                notificationService.sendOrderNotification(
                        receiverUser.getId(),
                        "user",
                        NotificationCopy.deliveredTitle(),
                        NotificationCopy.deliveredBody(order.getOrderNo()),
                        orderId
                );
                log.info("送达通知已发送给收件人, orderId: {}, receiverUserId: {}", orderId, receiverUser.getId());
            } else {
                log.info("收件人未注册平台账号，跳过消息通知, orderId: {}, receiverPhone: {}",
                        orderId, receiverAddress.getContactPhone());
            }
        } else {
            log.warn("订单缺少收件人地址信息，无法发送送达通知, orderId: {}", orderId);
        }

        log.info("确认送达成功, orderId: {}", orderId);
    }

    /**
     * Haversine 公式计算两点间距离（公里）
     */
    private double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @Autowired
    private OrderAddressMapper orderAddressMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TrackingService trackingService;

    @Override
    public List<CourierTaskDTO> getCurrentTasks(Long courierId) {
        log.debug("获取配送员当前任务, courierId: {}", courierId);

        // 获取配送员的所有进行中的任务
        List<DeliveryTask> tasks = deliveryTaskMapper.selectByCourierIdAndStatuses(courierId,
            List.of(OrderStatus.AWAITING_COURIER_CONFIRM.getCode(),
                    OrderStatus.AWAITING_PICKUP.getCode(),
                    OrderStatus.PICKED_UP.getCode(),
                    OrderStatus.IN_TRANSIT.getCode()));

        // 转换为 DTO
        List<CourierTaskDTO> result = new ArrayList<>();
        for (DeliveryTask task : tasks) {
            Order order = orderMapper.selectById(task.getOrderId());
            if (order == null) {
                continue;
            }

            // 获取寄件人地址
            OrderAddress senderAddress = orderAddressMapper.selectByOrderIdAndType(task.getOrderId(), "sender");
            // 获取收件人地址
            OrderAddress receiverAddress = orderAddressMapper.selectByOrderIdAndType(task.getOrderId(), "receiver");

            CourierTaskDTO dto = new CourierTaskDTO();
            dto.setId(task.getId());
            // 将 orderId 转为字符串，避免前端 JavaScript 精度丢失问题
            dto.setOrderId(task.getOrderId().toString());
            dto.setOrderNo(order.getOrderNo());
            dto.setOrderType(order.getOrderType());

            // 寄件人信息
            if (senderAddress != null) {
                dto.setSenderName(senderAddress.getContactName());
                dto.setSenderPhone(senderAddress.getContactPhone());
                dto.setSenderProvince(senderAddress.getProvince());
                dto.setSenderCity(senderAddress.getCity());
                dto.setSenderDistrict(senderAddress.getDistrict());
                dto.setSenderAddress(senderAddress.getDetailAddress());
                dto.setSenderLatitude(senderAddress.getLatitude());
                dto.setSenderLongitude(senderAddress.getLongitude());
            }

            // 收件人信息
            if (receiverAddress != null) {
                dto.setReceiverName(receiverAddress.getContactName());
                dto.setReceiverPhone(receiverAddress.getContactPhone());
                dto.setReceiverProvince(receiverAddress.getProvince());
                dto.setReceiverCity(receiverAddress.getCity());
                dto.setReceiverDistrict(receiverAddress.getDistrict());
                dto.setReceiverAddress(receiverAddress.getDetailAddress());
                dto.setReceiverLatitude(receiverAddress.getLatitude());
                dto.setReceiverLongitude(receiverAddress.getLongitude());
            }

            // 物品信息
            dto.setGoodsDescription(order.getGoodsDescription());
            dto.setGoodsWeight(order.getGoodsWeight());
            dto.setTotalAmount(order.getTotalAmount());

            // 配送费 = 总金额的 90%
            if (order.getTotalAmount() != null) {
                dto.setDeliveryFee(order.getTotalAmount().multiply(new BigDecimal("0.9")));
            }

            // 时间信息
            dto.setCreateTime(order.getCreateTime());
            dto.setAcceptTime(task.getAcceptTime());
            dto.setPickupTime(task.getPickupTime());
            dto.setDeliveryTime(task.getDeliveryTime());

            // 以订单主状态为准，与 statusDesc 一致，避免 task 表滞后导致前端只显示「已揽件」却无法流转
            // 使用 task 表的状态（与查询条件一致），避免 orders 表与 delivery_tasks 表状态不一致导致前端展示错误
            String taskStatus = task.getStatus();
            dto.setStatus(taskStatus);
            dto.setStatusDesc(OrderStatus.getDescByCode(taskStatus));

            result.add(dto);
        }

        return result;
    }

    @Override
    public List<CourierTaskDTO> getPendingConfirmTasks(Long courierId) {
        log.debug("获取配送员待确认订单, courierId: {}", courierId);

        // 获取待确认状态的任务
        List<DeliveryTask> tasks = deliveryTaskMapper.selectByCourierIdAndStatuses(courierId,
            List.of(OrderStatus.AWAITING_COURIER_CONFIRM.getCode()));

        // 转换为 DTO
        List<CourierTaskDTO> result = new ArrayList<>();
        for (DeliveryTask task : tasks) {
            Order order = orderMapper.selectById(task.getOrderId());
            if (order == null) {
                continue;
            }

            // 获取寄件人地址
            OrderAddress senderAddress = orderAddressMapper.selectByOrderIdAndType(task.getOrderId(), "sender");
            // 获取收件人地址
            OrderAddress receiverAddress = orderAddressMapper.selectByOrderIdAndType(task.getOrderId(), "receiver");

            CourierTaskDTO dto = new CourierTaskDTO();
            dto.setId(task.getId());
            // 将 orderId 转为字符串，避免前端 JavaScript 精度丢失问题
            dto.setOrderId(task.getOrderId().toString());
            dto.setOrderNo(order.getOrderNo());
            dto.setOrderType(order.getOrderType());

            // 寄件人信息
            if (senderAddress != null) {
                dto.setSenderName(senderAddress.getContactName());
                dto.setSenderPhone(senderAddress.getContactPhone());
                dto.setSenderProvince(senderAddress.getProvince());
                dto.setSenderCity(senderAddress.getCity());
                dto.setSenderDistrict(senderAddress.getDistrict());
                dto.setSenderAddress(senderAddress.getDetailAddress());
                dto.setSenderLatitude(senderAddress.getLatitude());
                dto.setSenderLongitude(senderAddress.getLongitude());
            }

            // 收件人信息
            if (receiverAddress != null) {
                dto.setReceiverName(receiverAddress.getContactName());
                dto.setReceiverPhone(receiverAddress.getContactPhone());
                dto.setReceiverProvince(receiverAddress.getProvince());
                dto.setReceiverCity(receiverAddress.getCity());
                dto.setReceiverDistrict(receiverAddress.getDistrict());
                dto.setReceiverAddress(receiverAddress.getDetailAddress());
                dto.setReceiverLatitude(receiverAddress.getLatitude());
                dto.setReceiverLongitude(receiverAddress.getLongitude());
            }

            // 物品信息
            dto.setGoodsDescription(order.getGoodsDescription());
            dto.setGoodsWeight(order.getGoodsWeight());
            dto.setTotalAmount(order.getTotalAmount());

            // 配送费 = 总金额的 90%
            if (order.getTotalAmount() != null) {
                dto.setDeliveryFee(order.getTotalAmount().multiply(new BigDecimal("0.9")));
            }

            // 时间信息
            dto.setCreateTime(order.getCreateTime());
            dto.setAcceptTime(task.getAcceptTime());
            dto.setPickupTime(task.getPickupTime());
            dto.setDeliveryTime(task.getDeliveryTime());

            // 使用 task 表的状态（与查询条件一致），避免 orders 表与 delivery_tasks 表状态不一致导致前端展示错误
            String taskStatus = task.getStatus();
            dto.setStatus(taskStatus);
            dto.setStatusDesc(OrderStatus.getDescByCode(taskStatus));

            result.add(dto);
        }

        log.debug("获取到{}个待确认订单", result.size());
        return result;
    }

    @Override
    public List<LogisticsEvent> getOrderEvents(Long orderId) {
        log.debug("获取订单物流事件, orderId: {}", orderId);
        return logisticsEventMapper.selectByOrderId(orderId);
    }

    @Override
    @Transactional
    public void reportException(Long orderId, Long courierId, String exceptionType, String description) {
        log.info("上报异常, orderId: {}, courierId: {}, type: {}", orderId, courierId, exceptionType);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        DeliveryTask task = deliveryTaskMapper.selectByCourierIdAndOrderId(courierId, orderId);
        if (task == null || "rejected".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此订单");
        }

        orderStatusValidator.requireTransition(order.getStatus(), OrderStatus.EXCEPTION.getCode());

        // 若配送员未指定异常类型，根据订单状态自动推断
        String et;
        if (exceptionType != null && !exceptionType.isBlank()) {
            et = exceptionType.trim();
        } else {
            String orderStatus = order.getStatus();
            if (OrderStatus.AWAITING_COURIER_CONFIRM.getCode().equals(orderStatus)
                    || OrderStatus.AWAITING_PICKUP.getCode().equals(orderStatus)) {
                et = OrderExceptionType.COURIER_NOT_RESPONDING.getCode();
            } else if (OrderStatus.PICKED_UP.getCode().equals(orderStatus)) {
                et = OrderExceptionType.PICKUP_ISSUE.getCode();
            } else {
                et = OrderExceptionType.TRANSIT_ISSUE.getCode();
            }
        }

        String desc = description != null && !description.isBlank() ? description.trim() : "";

        orderMapper.updateStatus(orderId, OrderStatus.EXCEPTION.getCode());
        orderMapper.markException(orderId, et);
        deliveryTaskMapper.updateStatus(task.getId(), OrderStatus.EXCEPTION.getCode());

        LogisticsEvent event = new LogisticsEvent();
        event.setOrderId(orderId);
        event.setStatus(OrderStatus.EXCEPTION.getCode());
        event.setDescription("配送环节问题已记录，客服将尽快核实" + (desc.isEmpty() ? "" : ("：" + desc)));
        event.setOperatorId(courierId);
        event.setCreateTime(LocalDateTime.now());
        logisticsEventMapper.insert(event);

        // 同步 Redis：异常订单从配送员活跃订单中移除，并更新状态和订单计数
        courierStatusService.finishDelivery(courierId, orderId);

        log.info("异常上报成功, orderId: {}, exceptionType={}", orderId, et);
    }

    @Override
    @Transactional
    public boolean confirmOrder(Long orderId, Long courierId) {
        log.info("配送员确认订单, orderId: {}, courierId: {}", orderId, courierId);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.error("订单不存在, orderId: {}", orderId);
            return false;
        }

        if (!OrderStatus.AWAITING_COURIER_CONFIRM.getCode().equals(order.getStatus())) {
            log.error("订单状态不允许确认, 当前状态: {}", order.getStatus());
            return false;
        }

        DeliveryTask task = deliveryTaskMapper.selectByCourierIdAndOrderId(courierId, orderId);
        if (task == null || "rejected".equals(task.getStatus())) {
            log.error("无权确认此订单");
            return false;
        }

        try {
            orderMapper.updateStatus(orderId, OrderStatus.AWAITING_PICKUP.getCode());
            deliveryTaskMapper.updateStatus(task.getId(), OrderStatus.AWAITING_PICKUP.getCode());
            deliveryTaskMapper.updateAcceptTime(task.getId());

            // 同步更新 Redis：配送员确认接单后，增加订单计数并更新状态
            courierStatusService.syncActiveOrderKeysFromDb(courierId);

            LogisticsEvent event = new LogisticsEvent();
            event.setOrderId(orderId);
            event.setStatus(OrderStatus.AWAITING_PICKUP.getCode());
            event.setDescription("配送员已接单，等待揽件");
            event.setOperatorId(courierId);
            event.setCreateTime(LocalDateTime.now());
            logisticsEventMapper.insert(event);

            log.info("配送员确认订单成功, orderId: {}", orderId);
            return true;
        } catch (Exception e) {
            log.error("确认订单失败, orderId: {}", orderId, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean rejectOrder(Long orderId, Long courierId, String reason) {
        log.info("配送员拒绝订单, orderId: {}, courierId: {}, reason: {}", orderId, courierId, reason);

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.error("订单不存在, orderId: {}", orderId);
            return false;
        }

        if (!OrderStatus.AWAITING_COURIER_CONFIRM.getCode().equals(order.getStatus())) {
            log.error("订单状态不允许拒绝, 当前状态: {}", order.getStatus());
            return false;
        }

        DeliveryTask task = deliveryTaskMapper.selectByCourierIdAndOrderId(courierId, orderId);
        if (task == null || "rejected".equals(task.getStatus())) {
            log.error("无权拒绝此订单");
            return false;
        }

        try {
            deliveryTaskMapper.updateStatus(task.getId(), "rejected");
            orderMapper.updateStatus(orderId, OrderStatus.PAID.getCode());
            orderMapper.clearCourier(orderId);

            courierStatusService.finishDelivery(courierId, orderId);

            LogisticsEvent event = new LogisticsEvent();
            event.setOrderId(orderId);
            event.setStatus("rejected");
            event.setDescription("配送员拒绝接单, 原因: " + (reason != null ? reason : "无"));
            event.setOperatorId(courierId);
            event.setCreateTime(LocalDateTime.now());
            logisticsEventMapper.insert(event);

            log.info("配送员拒绝订单成功, orderId: {}", orderId);
            return true;
        } catch (Exception e) {
            log.error("拒绝订单失败, orderId: {}", orderId, e);
            return false;
        }
    }

    @Override
    @Transactional
    public void startBatchDelivery(Long courierId) {
        log.info("配送员开始批量配送, courierId: {}", courierId);

        List<Order> pickedUpOrders = orderMapper.selectByCourierId(courierId).stream()
                .filter(order -> OrderStatus.PICKED_UP.getCode().equals(order.getStatus()))
                .toList();

        if (pickedUpOrders.isEmpty()) {
            log.warn("配送员没有已揽件的订单, courierId: {}", courierId);
            return;
        }

        // 1. busy 并从可派单池移除（不绑定单一 orderId，订单集合在步骤 3 后按库表同步到 Redis）
        courierStatusService.startDelivery(courierId, null);

        // 2. 将所有已揽件订单状态更新为运输中
        for (Order order : pickedUpOrders) {
            Long orderId = order.getId();

            // 更新订单状态
            orderMapper.updateStatus(orderId, OrderStatus.IN_TRANSIT.getCode());

            // 更新配送任务状态
            DeliveryTask task = deliveryTaskMapper.selectByCourierIdAndOrderId(courierId, orderId);
            if (task != null && !"rejected".equals(task.getStatus())) {
                deliveryTaskMapper.updateStatus(task.getId(), OrderStatus.IN_TRANSIT.getCode());
            }

            // 记录物流事件
            LogisticsEvent event = new LogisticsEvent();
            event.setOrderId(orderId);
            event.setStatus(OrderStatus.IN_TRANSIT.getCode());
            event.setDescription("开始配送，订单进入运输中状态");
            event.setOperatorId(courierId);
            event.setCreateTime(LocalDateTime.now());
            logisticsEventMapper.insert(event);

            log.info("订单开始配送, orderId: {}, courierId: {}", orderId, courierId);
        }

        courierStatusService.syncActiveOrderKeysFromDb(courierId);

        log.info("配送员开始批量配送完成, courierId: {}, 订单数量: {}", courierId, pickedUpOrders.size());
    }
}
