package com.logistics.api.service.impl;

import com.logistics.api.common.BusinessException;
import com.logistics.api.common.ErrorCode;
import com.logistics.api.dto.CreateOrderRequest;
import com.logistics.api.dto.OrderDTO;
import com.logistics.api.enums.OrderStatus;
import com.logistics.api.mapper.*;
import com.logistics.api.model.*;
import com.logistics.api.service.NotificationService;
import com.logistics.api.service.support.NotificationCopy;
import com.logistics.api.service.OrderService;
import com.logistics.api.service.PricingService;
import com.logistics.api.service.GrabOrderService;
import com.logistics.api.service.CourierStatusService;
import com.logistics.api.service.WalletService;
import com.logistics.api.config.OrderAutoExceptionProperties;
import com.logistics.api.enums.OrderExceptionType;
import com.logistics.api.service.support.OrderStatusValidator;
import com.logistics.api.service.support.OrderViewerPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Comparator;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private OrderAddressMapper orderAddressMapper;
    
    @Autowired
    private DeliveryTaskMapper deliveryTaskMapper;

    @Autowired
    private OrderStatusValidator orderStatusValidator;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private PricingService pricingService;
    
    @Autowired
    private GoodsTypeMapper goodsTypeMapper;
    
    @Autowired
    private LogisticsEventMapper logisticsEventMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private GrabOrderService grabOrderService;

    @Autowired
    private OrderAutoExceptionProperties orderAutoExceptionProperties;

    @Autowired
    private CourierStatusService courierStatusService;

    @Autowired
    private WalletService walletService;

    private static final DateTimeFormatter ORDER_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();

    private String generateOrderNo() {
        String prefix = "LG";
        String dateStr = LocalDateTime.now().format(ORDER_NO_FORMATTER);
        String randomStr = String.format("%04d", RANDOM.nextInt(10000));
        return prefix + dateStr + randomStr;
    }

    @Override
    @Transactional
    public Order createOrder(CreateOrderRequest request, String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(user.getId());
        order.setGoodsTypeId(request.getGoodsTypeId());
        order.setGoodsDescription(request.getGoodsDescription());
        order.setGoodsWeight(request.getGoodsWeight() != null ? request.getGoodsWeight() : BigDecimal.ONE);
        order.setStatus(OrderStatus.PENDING.getCode());
        order.setOrderType(request.getOrderType() != null ? request.getOrderType() : "standard");
        order.setPayType("prepay");

        BigDecimal totalAmount = calculateOrderAmount(request);
        order.setTotalAmount(totalAmount);
        order.setActualAmount(totalAmount);
        order.setRemark(request.getRemark());

        orderMapper.insert(order);

        OrderAddress senderAddress = new OrderAddress();
        senderAddress.setOrderId(order.getId());
        senderAddress.setType("sender");
        senderAddress.setContactName(request.getSenderName());
        senderAddress.setContactPhone(request.getSenderPhone());
        senderAddress.setProvince(request.getSenderProvince());
        senderAddress.setCity(request.getSenderCity());
        senderAddress.setDistrict(request.getSenderDistrict());
        senderAddress.setDetailAddress(request.getSenderDetailAddress());
        senderAddress.setLatitude(request.getSenderLatitude());
        senderAddress.setLongitude(request.getSenderLongitude());
        orderAddressMapper.insert(senderAddress);

        OrderAddress receiverAddress = new OrderAddress();
        receiverAddress.setOrderId(order.getId());
        receiverAddress.setType("receiver");
        receiverAddress.setContactName(request.getReceiverName());
        receiverAddress.setContactPhone(request.getReceiverPhone());
        receiverAddress.setProvince(request.getReceiverProvince());
        receiverAddress.setCity(request.getReceiverCity());
        receiverAddress.setDistrict(request.getReceiverDistrict());
        receiverAddress.setDetailAddress(request.getReceiverDetailAddress());
        receiverAddress.setLatitude(request.getReceiverLatitude());
        receiverAddress.setLongitude(request.getReceiverLongitude());
        orderAddressMapper.insert(receiverAddress);

        recordLogisticsEvent(order.getId(), OrderStatus.PENDING.getCode(), "订单已创建，等待支付", null);

        // 发送订单创建通知
        notificationService.sendOrderNotification(
                user.getId(),
                "user",
                NotificationCopy.orderCreatedTitle(),
                NotificationCopy.orderCreatedBody(order.getOrderNo()),
                order.getId()
        );

        log.info("订单创建成功, orderId={}, orderNo={}", order.getId(), order.getOrderNo());
        return order;
    }

    private BigDecimal calculateOrderAmount(CreateOrderRequest request) {
        BigDecimal weight = request.getGoodsWeight();
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            weight = BigDecimal.ONE;
        }

        BigDecimal distance = BigDecimal.ZERO;
        if (request.getSenderLatitude() != null && request.getSenderLongitude() != null &&
            request.getReceiverLatitude() != null && request.getReceiverLongitude() != null) {
            distance = pricingService.calculateDistance(
                request.getSenderLatitude(), request.getSenderLongitude(),
                request.getReceiverLatitude(), request.getReceiverLongitude()
            );
        }

        String orderType = request.getOrderType() != null ? request.getOrderType() : "standard";

        return pricingService.calculateOrderPrice(
            request.getGoodsTypeId(), 
            weight, 
            distance, 
            orderType
        );
    }

    private void recordLogisticsEvent(Long orderId, String status, String description, Long operatorId) {
        LogisticsEvent event = new LogisticsEvent();
        event.setOrderId(orderId);
        event.setStatus(status);
        event.setDescription(description);
        event.setOperatorId(operatorId);
        event.setCreateTime(LocalDateTime.now());
        logisticsEventMapper.insert(event);
    }

    @Override
    public List<OrderDTO> getUserOrders(String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Map<Long, Order> merged = new LinkedHashMap<>();
        for (Order o : orderMapper.selectByUserId(user.getId())) {
            merged.put(o.getId(), o);
        }

        String pnorm = OrderViewerPolicy.normalizePhone(user.getPhone());
        if (!pnorm.isEmpty()) {
            for (Order o : orderMapper.selectByReceiverNormalizedPhone(pnorm)) {
                if (merged.containsKey(o.getId())) {
                    continue;
                }
                DeliveryTask t = deliveryTaskMapper.selectByOrderId(o.getId());
                if (!OrderViewerPolicy.receiverCanViewOrder(o, t)) {
                    continue;
                }
                merged.put(o.getId(), o);
            }
        }

        return merged.values().stream()
                .sorted(Comparator.comparing(Order::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(o -> {
                    OrderDTO dto = convertToDTO(o);
                    enrichCustomerViewerFlags(dto, user, o);
                    return dto;
                })
                .peek(this::maskCourierForEndCustomerIfNeeded)
                .collect(Collectors.toList());
    }

    private void enrichCustomerViewerFlags(OrderDTO dto, User user, Order order) {
        boolean isSender = user.getId().equals(order.getUserId());
        dto.setViewerRole(isSender ? "sender" : "receiver");
        boolean recvMatch = OrderViewerPolicy.phonesMatch(user.getPhone(), dto.getReceiverPhone());
        dto.setReceiverPhoneMatch(recvMatch);
        boolean delivered = OrderStatus.DELIVERED.getCode().equals(order.getStatus());
        boolean done = OrderStatus.COMPLETED.getCode().equals(order.getStatus());
        dto.setCanConfirmReceipt(recvMatch && delivered);
        dto.setCanReviewOrder(recvMatch && (delivered || done));
    }

    /**
     * 用户端在「已送达」前不展示配送员身份信息（与 App 展示策略一致）。
     */
    private void maskCourierForEndCustomerIfNeeded(OrderDTO dto) {
        if (dto == null || dto.getStatus() == null) {
            return;
        }
        String s = dto.getStatus();
        if (OrderStatus.DELIVERED.getCode().equals(s) || OrderStatus.COMPLETED.getCode().equals(s)) {
            return;
        }
        dto.setCourierId(null);
        dto.setCourierName(null);
        dto.setCourierPhone(null);
    }

    private OrderDTO convertToDTO(Order order) {
        OrderDTO dto = OrderDTO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .courierId(order.getCourierId())
                .goodsTypeId(order.getGoodsTypeId())
                .goodsDescription(order.getGoodsDescription())
                .goodsWeight(order.getGoodsWeight())
                .status(order.getStatus())
                .statusDesc(OrderStatus.getDescByCode(order.getStatus()))
                .orderType(order.getOrderType())
                .totalAmount(order.getTotalAmount())
                .actualAmount(order.getActualAmount())
                .payType(order.getPayType())
                .cancelReason(order.getCancelReason())
                .cancelTime(order.getCancelTime())
                .remark(order.getRemark())
                .createTime(order.getCreateTime())
                .updateTime(order.getUpdateTime())
                .build();
        dto.setIsException(order.getIsException() != null && order.getIsException() == 1);
        dto.setExceptionType(order.getExceptionType());
        dto.setExceptionRecoverable(OrderExceptionType.isRecoverableByExceptionTypeCode(order.getExceptionType()));
        dto.setAllowedActions(OrderExceptionType.getAllowedActions(order.getExceptionType()));

        List<OrderAddress> addresses = orderAddressMapper.selectByOrderId(order.getId());
        for (OrderAddress address : addresses) {
            if ("sender".equals(address.getType())) {
                dto.setSenderName(address.getContactName());
                dto.setSenderPhone(address.getContactPhone());
                dto.setSenderProvince(address.getProvince());
                dto.setSenderCity(address.getCity());
                dto.setSenderDistrict(address.getDistrict());
                dto.setSenderDetailAddress(address.getDetailAddress());
                dto.setSenderLatitude(address.getLatitude());
                dto.setSenderLongitude(address.getLongitude());
            } else if ("receiver".equals(address.getType())) {
                dto.setReceiverName(address.getContactName());
                dto.setReceiverPhone(address.getContactPhone());
                dto.setReceiverProvince(address.getProvince());
                dto.setReceiverCity(address.getCity());
                dto.setReceiverDistrict(address.getDistrict());
                dto.setReceiverDetailAddress(address.getDetailAddress());
                dto.setReceiverLatitude(address.getLatitude());
                dto.setReceiverLongitude(address.getLongitude());
            }
        }

        if (order.getGoodsTypeId() != null) {
            GoodsType goodsType = goodsTypeMapper.selectById(order.getGoodsTypeId());
            if (goodsType != null) {
                dto.setGoodsTypeName(goodsType.getName());
            }
        }

        if (order.getCourierId() != null) {
            User courier = userMapper.selectById(order.getCourierId());
            if (courier != null) {
                dto.setCourierName(courier.getName());
                dto.setCourierPhone(courier.getPhone());
            }
        }

        return dto;
    }

    @Override
    public OrderDTO getOrderDetailByOrderNo(String orderNo, String username) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "订单号不能为空");
        }
        String trimmed = orderNo.trim();
        Order order = orderMapper.selectByOrderNo(trimmed);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return getOrderDetail(order.getId(), username);
    }

    @Override
    public OrderDTO getOrderDetail(Long orderId, String username) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        boolean isSender = user.getId().equals(order.getUserId());
        boolean isAssignedCourier = order.getCourierId() != null && user.getId().equals(order.getCourierId());
        boolean isAdmin = "admin".equals(user.getRole());

        OrderAddress recvAddr = orderAddressMapper.selectByOrderIdAndType(orderId, "receiver");
        boolean isReceiverAccount = OrderViewerPolicy.phonesMatch(user.getPhone(),
                recvAddr != null ? recvAddr.getContactPhone() : null);

        if (isAdmin) {
            // admin 全可查
        } else if (isAssignedCourier) {
            // 订单所属配送员
        } else if (isSender) {
            // 下单人 / 寄件侧
        } else if (isReceiverAccount) {
            DeliveryTask task = deliveryTaskMapper.selectByOrderId(orderId);
            if (!OrderViewerPolicy.receiverCanViewOrder(order, task)) {
                throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
            }
        } else {
            throw new BusinessException(ErrorCode.ORDER_NOT_OWNER, "无权查看此订单");
        }

        OrderDTO dto = convertToDTO(order);
        if (isSender || isReceiverAccount) {
            enrichCustomerViewerFlags(dto, user, order);
        }
        if ((isSender || isReceiverAccount) && !isAdmin) {
            maskCourierForEndCustomerIfNeeded(dto);
        }
        return dto;
    }

    @Override
    public void checkOrderViewPermission(String username, Long orderId) {
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (user.getId().equals(order.getUserId()) || "admin".equals(user.getRole())) {
            return;
        }
        if (order.getCourierId() != null && user.getId().equals(order.getCourierId())) {
            return;
        }
        DeliveryTask task = deliveryTaskMapper.selectByOrderId(orderId);
        if (task != null && user.getId().equals(task.getCourierId())) {
            return;
        }
        OrderAddress recvAddr = orderAddressMapper.selectByOrderIdAndType(orderId, "receiver");
        if (OrderViewerPolicy.phonesMatch(user.getPhone(),
                recvAddr != null ? recvAddr.getContactPhone() : null)) {
            if (OrderViewerPolicy.receiverCanViewOrder(order, task)) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.ORDER_NOT_OWNER, "无权查看该订单的物流信息");
    }

    @Override
    public org.springframework.data.domain.Page<OrderDTO> getOrdersWithFilter(String status, String keyword, String startDate, String endDate, org.springframework.data.domain.Pageable pageable) {
        List<Order> allOrders = orderMapper.selectPendingDispatch();
        
        List<Order> filtered = allOrders.stream()
            .filter(order -> status == null || status.isEmpty() || status.equals(order.getStatus()))
            .filter(order -> keyword == null || keyword.isEmpty() || 
                (order.getOrderNo() != null && order.getOrderNo().contains(keyword)) ||
                (order.getGoodsDescription() != null && order.getGoodsDescription().contains(keyword)))
            .collect(Collectors.toList());
        
        List<OrderDTO> dtos = filtered.stream().map(this::convertToDTO).collect(Collectors.toList());
        
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), dtos.size());
        List<OrderDTO> pageContent = start < dtos.size() ? dtos.subList(start, end) : List.of();
        
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, dtos.size());
    }

    @Override
    public OrderDTO getOrderDetailById(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return convertToDTO(order);
    }



    @Override
    public Map<String, Object> getOrderStatistics() {
        Map<String, Object> stats = new HashMap<>();
        long total = orderMapper.countTotalOrders();
        stats.put("totalOrders", total);

        List<Map<String, Object>> statusRows = orderMapper.selectOrderCountGroupByStatus();
        stats.put("pendingCount", countStatusFromGroupRows(statusRows, OrderStatus.PENDING.getCode()));
        stats.put("paidCount", countStatusFromGroupRows(statusRows, OrderStatus.PAID.getCode()));
        stats.put("awaitingConfirmCount", countStatusFromGroupRows(statusRows, OrderStatus.AWAITING_COURIER_CONFIRM.getCode()));
        stats.put("awaitingPickupCount", countStatusFromGroupRows(statusRows, OrderStatus.AWAITING_PICKUP.getCode()));
        stats.put("deliveredCount", countStatusFromGroupRows(statusRows, OrderStatus.DELIVERED.getCode()));
        stats.put("cancelledCount", countStatusFromGroupRows(statusRows, OrderStatus.CANCELLED.getCode()));
        stats.put("exceptionCount", countStatusFromGroupRows(statusRows, OrderStatus.EXCEPTION.getCode()));

        BigDecimal totalRevenue = orderMapper.sumActualAmountByStatus(OrderStatus.DELIVERED.getCode());
        stats.put("totalRevenue", totalRevenue != null ? totalRevenue.multiply(new BigDecimal("0.10")) : BigDecimal.ZERO);
        return stats;
    }

    @Override
    public Map<String, Object> getTodayOrderStats() {
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<String> doneStatuses = List.of(
                OrderStatus.DELIVERED.getCode(), OrderStatus.COMPLETED.getCode());
        List<String> terminalForPipeline = List.of(
                OrderStatus.DELIVERED.getCode(),
                OrderStatus.COMPLETED.getCode(),
                OrderStatus.CANCELLED.getCode());

        long newToday = orderMapper.countByCreateTimeBetween(dayStart, dayEnd);
        long completedToday = orderMapper.countByUpdateTimeBetweenAndStatuses(
                dayStart, dayEnd, doneStatuses);
        BigDecimal todayRevenue = orderMapper.sumActualByUpdateTimeBetweenAndStatuses(
                dayStart, dayEnd, doneStatuses);
        long inPipeline = orderMapper.countWhereStatusNotIn(terminalForPipeline);

        Map<String, Object> stats = new HashMap<>();
        stats.put("todayTotal", newToday);
        stats.put("todayDelivered", completedToday);
        stats.put("todayRevenue", todayRevenue != null ? todayRevenue.multiply(new BigDecimal("0.10")) : BigDecimal.ZERO);
        stats.put("totalOrders", newToday);
        stats.put("completedOrders", completedToday);
        stats.put("totalRevenue", todayRevenue != null ? todayRevenue.multiply(new BigDecimal("0.10")) : BigDecimal.ZERO);
        stats.put("pendingOrders", inPipeline);
        return stats;
    }

    @Override
    public List<Map<String, Object>> getOrderTrend(LocalDateTime start, LocalDateTime end, String groupBy) {
        List<Map<String, Object>> rows = "hour".equals(groupBy)
                ? orderMapper.selectCreateOrderTrendByHour(start, end)
                : orderMapper.selectCreateOrderTrendByDay(start, end);
        return rows.stream()
                .map(row -> {
                    Object b = row.get("bucket");
                    String dateKey = b == null ? "" : b.toString();
                    Object c = row.get("cnt");
                    long count = c instanceof Number ? ((Number) c).longValue() : 0L;
                    Map<String, Object> item = new HashMap<>();
                    item.put("date", dateKey);
                    item.put("count", count);
                    return item;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Long> getOrderStatusDistribution() {
        List<Map<String, Object>> rows = orderMapper.selectOrderCountGroupByStatus();
        Map<String, Long> distribution = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object s = row.get("s");
            Object c = row.get("c");
            if (s == null) {
                continue;
            }
            long cnt = c instanceof Number ? ((Number) c).longValue() : 0L;
            distribution.put(String.valueOf(s), cnt);
        }
        return distribution;
    }

    @Override
    public BigDecimal getTotalRevenue(LocalDateTime start, LocalDateTime end) {
        BigDecimal revenue = orderMapper.sumActualByCreateTimeBetweenAndStatuses(
                start, end, List.of(OrderStatus.DELIVERED.getCode(), OrderStatus.COMPLETED.getCode()));
        return revenue != null ? revenue.multiply(new BigDecimal("0.10")) : BigDecimal.ZERO;
    }

    @Override
    public List<Map<String, Object>> getRevenueTrend(LocalDateTime start, LocalDateTime end) {
        return orderMapper.selectRevenueTrendByUpdateDay(start, end).stream()
                .map(row -> {
                    Object b = row.get("bucket");
                    String dateKey = b == null ? "" : b.toString();
                    Object r = row.get("revenue");
                    BigDecimal revenue = r instanceof BigDecimal
                            ? (BigDecimal) r
                            : (r instanceof Number
                            ? BigDecimal.valueOf(((Number) r).doubleValue())
                            : BigDecimal.ZERO);
                    Map<String, Object> item = new HashMap<>();
                    item.put("date", dateKey);
                    item.put("revenue", revenue.multiply(new BigDecimal("0.10")));
                    return item;
                })
                .collect(Collectors.toList());
    }

    private static long countStatusFromGroupRows(List<Map<String, Object>> rows, String status) {
        if (rows == null) {
            return 0L;
        }
        for (Map<String, Object> row : rows) {
            Object s = row.get("s");
            if (s != null && status.equals(String.valueOf(s))) {
                Object c = row.get("c");
                return c instanceof Number ? ((Number) c).longValue() : 0L;
            }
        }
        return 0L;
    }

    @Override
    public List<OrderDTO> getOrdersByStatus(String status) {
        List<Order> orders = orderMapper.selectByStatus(status);
        return orders.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getExceptionOrders(String type, int page, int size) {
        List<Order> exceptionOrders = orderMapper.selectExceptionOrders();
        
        List<Order> filtered = exceptionOrders.stream()
            .filter(o -> {
                if (type == null || type.isEmpty()) {
                    return true;
                }
                return type.equals(o.getStatus())
                        || (o.getExceptionType() != null && type.equals(o.getExceptionType()));
            })
            .collect(Collectors.toList());
        
        int total = filtered.size();
        int start = page * size;
        int end = Math.min(start + size, total);
        
        List<OrderDTO> items = start < total ? 
            filtered.subList(start, end).stream().map(this::convertToDTO).collect(Collectors.toList()) 
            : List.of();
        
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    @Transactional
    public void adminRecoverException(Long orderId, String action, String remark) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!OrderStatus.EXCEPTION.getCode().equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "当前订单不是异常状态，无法解除异常");
        }
        if (action == null || action.isBlank()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "恢复动作不能为空");
        }
        String act = action.trim().toLowerCase();

        // 校验动作是否被允许
        if (!OrderExceptionType.isActionAllowed(order.getExceptionType(), act)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR,
                    "该异常类型不允许执行「" + act + "」操作");
        }

        DeliveryTask task = deliveryTaskMapper.selectLatestByOrderId(orderId);

        String targetStatus;
        String eventDesc;

        switch (act) {
            case "continue" -> {
                // 继续配送：回到异常前的状态（优先从 logistics_events 获取）
                targetStatus = getPreExceptionStatus(orderId);
                if (targetStatus == null) {
                    targetStatus = OrderStatus.AWAITING_PICKUP.getCode();
                }
                orderStatusValidator.requireTransition(OrderStatus.EXCEPTION.getCode(), targetStatus);
                if (task != null) {
                    deliveryTaskMapper.updateStatus(task.getId(), targetStatus);
                }
                eventDesc = "管理员恢复配送，订单继续流转";
            }
            case "reassign" -> {
                // 改派：清除原配送员，回到 paid 重新分配
                targetStatus = OrderStatus.PAID.getCode();
                orderStatusValidator.requireTransition(OrderStatus.EXCEPTION.getCode(), targetStatus);
                if (task != null && task.getCourierId() != null) {
                    deliveryTaskMapper.updateStatus(task.getId(), "rejected");
                    try {
                        courierStatusService.finishDelivery(task.getCourierId(), orderId);
                    } catch (Exception e) {
                        log.warn("改派时 finishDelivery: orderId={}, {}", orderId, e.getMessage());
                    }
                }
                orderMapper.clearCourier(orderId);
                eventDesc = "管理员改派，订单重新进入分配队列";
            }
            case "cancel" -> {
                // 取消订单：关闭并退款
                targetStatus = OrderStatus.CANCELLED.getCode();
                orderStatusValidator.requireTransition(OrderStatus.EXCEPTION.getCode(), targetStatus);
                if (task != null && task.getCourierId() != null) {
                    try {
                        courierStatusService.finishDelivery(task.getCourierId(), orderId);
                    } catch (Exception e) {
                        log.warn("取消时 finishDelivery: orderId={}, {}", orderId, e.getMessage());
                    }
                }
                orderMapper.clearCourier(orderId);
                if (task != null) {
                    deliveryTaskMapper.updateStatus(task.getId(), OrderStatus.CANCELLED.getCode());
                }
                eventDesc = "管理员关闭异常订单";

                // 自动退款
                BigDecimal actual = order.getActualAmount() != null ? order.getActualAmount() : BigDecimal.ZERO;
                BigDecimal refunded = walletService.getRefundedAmount(orderId);
                BigDecimal toRefund = actual.subtract(refunded).max(BigDecimal.ZERO);
                if (toRefund.compareTo(BigDecimal.ZERO) > 0) {
                    try {
                        walletService.refund(order.getUserId(), toRefund, orderId, order.getOrderNo(),
                                "异常订单关闭自动退款" + (remark != null && !remark.isBlank() ? "：" + remark : ""));
                        notificationService.sendNotification(
                                order.getUserId(),
                                "user",
                                "system",
                                NotificationCopy.refundSuccessTitle(),
                                NotificationCopy.refundSuccessBody(order.getOrderNo(), toRefund)
                        );
                    } catch (Exception e) {
                        log.warn("异常订单退款失败, orderId={}, err={}", orderId, e.getMessage());
                    }
                }
            }
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "不支持的恢复动作：" + act + "，仅支持 continue/reassign/cancel");
        }

        orderMapper.updateStatus(orderId, targetStatus);
        orderMapper.clearExceptionFlag(orderId);
        recordLogisticsEvent(orderId, targetStatus,
                eventDesc + (remark != null && !remark.isBlank() ? ("，" + remark) : ""),
                null);
        log.info("管理员恢复异常订单, orderId={}, action={}, targetStatus={}", orderId, act, targetStatus);
    }

    /**
     * 从配送任务推断异常前的订单状态。
     */
    private String inferPreExceptionStatus(DeliveryTask task) {
        if (task == null) {
            return null;
        }
        String taskStatus = task.getStatus();
        if (taskStatus == null) {
            return null;
        }
        return switch (taskStatus) {
            case "awaiting_courier_confirm" -> OrderStatus.AWAITING_COURIER_CONFIRM.getCode();
            case "awaiting_pickup" -> OrderStatus.AWAITING_PICKUP.getCode();
            case "picked_up" -> OrderStatus.PICKED_UP.getCode();
            case "in_transit" -> OrderStatus.IN_TRANSIT.getCode();
            case "delivered" -> OrderStatus.DELIVERED.getCode();
            default -> null;
        };
    }

    @Override
    public void runAutoExceptionRules() {
        if (!orderAutoExceptionProperties.isEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        // 待确认超时：标记为异常（配送员不确认属于异常）
        LocalDateTime confirmThreshold = now.minusMinutes(orderAutoExceptionProperties.getAwaitingCourierConfirmMinutes());
        List<Order> awaiting = orderMapper.selectByStatus(OrderStatus.AWAITING_COURIER_CONFIRM.getCode());
        if (awaiting != null) {
            for (Order o : awaiting) {
                LocalDateTime t = o.getDispatchTime() != null ? o.getDispatchTime() : o.getUpdateTime();
                if (t == null) {
                    t = o.getCreateTime();
                }
                if (t != null && t.isBefore(confirmThreshold)) {
                    tryApplySystemException(o.getId(), OrderExceptionType.COURIER_NOT_RESPONDING.getCode(),
                            "分配后超过 " + orderAutoExceptionProperties.getAwaitingCourierConfirmMinutes() + " 分钟未确认接单");
                }
            }
        }
    }

    private void tryApplySystemException(Long orderId, String exceptionTypeCode, String detailMessage) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return;
        }
        if (OrderStatus.EXCEPTION.getCode().equals(order.getStatus())) {
            return;
        }
        try {
            orderStatusValidator.requireTransition(order.getStatus(), OrderStatus.EXCEPTION.getCode());
        } catch (BusinessException e) {
            log.debug("跳过自动异常 orderId={}: {}", orderId, e.getMessage());
            return;
        }
        orderMapper.updateStatus(orderId, OrderStatus.EXCEPTION.getCode());
        orderMapper.markException(orderId, exceptionTypeCode);
        DeliveryTask task = deliveryTaskMapper.selectByOrderId(orderId);
        if (task != null) {
            deliveryTaskMapper.updateStatus(task.getId(), OrderStatus.EXCEPTION.getCode());
        }
        recordLogisticsEvent(orderId, OrderStatus.EXCEPTION.getCode(),
                "订单配送进度延迟，平台正在协调处理", null);
        log.info("系统自动异常 orderId={}, type={}", orderId, exceptionTypeCode);
    }



    @Override
    @Transactional
    public void processRefund(Long orderId, String refundType, BigDecimal amount, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }

        // 仅已支付及之后状态的订单才可退款；待支付订单无款可退
        String st = order.getStatus();
        if (OrderStatus.PENDING.getCode().equals(st)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR,
                    "订单尚未支付，无款可退，可直接取消订单");
        }

        // 已退款金额（通过钱包流水表汇总，不依赖订单表字段）
        BigDecimal refunded = walletService.getRefundedAmount(orderId);
        BigDecimal actual = order.getActualAmount() != null ? order.getActualAmount() : BigDecimal.ZERO;
        BigDecimal toRefund;

        if ("full".equals(refundType)) {
            toRefund = actual.subtract(refunded).max(BigDecimal.ZERO);
        } else if ("partial".equals(refundType) && amount != null) {
            toRefund = amount.min(actual.subtract(refunded)).max(BigDecimal.ZERO);
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "退款类型错误");
        }

        if (toRefund.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "已无剩余可退金额");
        }

        // 清理配送任务
        DeliveryTask refundTask = deliveryTaskMapper.selectLatestByOrderId(orderId);
        if (refundTask != null) {
            if (refundTask.getCourierId() != null) {
                try {
                    courierStatusService.finishDelivery(refundTask.getCourierId(), orderId);
                } catch (Exception e) {
                    log.warn("退款时 finishDelivery: orderId={}, {}", orderId, e.getMessage());
                }
            }
            deliveryTaskMapper.updateStatus(refundTask.getId(), OrderStatus.CANCELLED.getCode());
        }
        orderMapper.clearCourier(orderId);

        // 退款打入用户钱包（通过钱包流水表记录退款，不修改订单表）
        BigDecimal newRefunded = refunded.add(toRefund);
        try {
            walletService.refund(order.getUserId(), toRefund, orderId, order.getOrderNo(), reason);
        } catch (Exception e) {
            log.warn("钱包退款失败, orderId={}, err={}", orderId, e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_ERROR, "退款到钱包失败: " + e.getMessage());
        }

        orderMapper.clearExceptionFlag(orderId);
        orderMapper.updateStatus(orderId, OrderStatus.CANCELLED.getCode());

        // 退款成功通知用户（退款属于内部流程，不记录物流事件）
        try {
            notificationService.sendNotification(
                    order.getUserId(),
                    "user",
                    "system",
                    NotificationCopy.refundSuccessTitle(),
                    NotificationCopy.refundSuccessBody(order.getOrderNo(), toRefund)
            );
        } catch (Exception e) {
            log.warn("退款通知发送失败, orderId={}, err={}", orderId, e.getMessage());
        }

        log.info("订单退款处理完成, orderId={}, refundType={}, toRefund={}, newRefunded={}",
                orderId, refundType, toRefund, newRefunded);
    }

    @Override
    public Map<String, Object> searchOrders(String keyword, int page, int size) {
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        long total = orderMapper.countSearchAdmin(kw);
        int start = Math.max(0, page) * size;
        List<Order> slice = orderMapper.searchAdminPaged(kw, start, size);
        List<OrderDTO> items = slice.stream().map(this::convertToDTO).collect(Collectors.toList());
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public Map<String, Object> getOrderFullDetail(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        
        Map<String, Object> detail = new java.util.HashMap<>();
        detail.put("order", convertToDTO(order));
        
        // 异常订单：返回异常前状态，用于前端判断可恢复操作
        if (OrderStatus.EXCEPTION.getCode().equals(order.getStatus())) {
            String preStatus = getPreExceptionStatus(orderId);
            if (preStatus != null) {
                detail.put("preExceptionStatus", preStatus);
            }
        }
        
        List<OrderAddress> addresses = orderAddressMapper.selectByOrderId(orderId);
        for (OrderAddress address : addresses) {
            if ("sender".equals(address.getType())) {
                detail.put("senderAddress", address);
            } else if ("receiver".equals(address.getType())) {
                detail.put("receiverAddress", address);
            }
        }
        
        if (order.getCourierId() != null) {
            User courier = userMapper.selectById(order.getCourierId());
            if (courier != null) {
                Map<String, Object> courierInfo = new java.util.HashMap<>();
                courierInfo.put("id", courier.getId());
                courierInfo.put("name", courier.getName());
                courierInfo.put("phone", courier.getPhone());
                detail.put("courier", courierInfo);
            }
        }
        
        List<LogisticsEvent> events = logisticsEventMapper.selectByOrderId(orderId);
        detail.put("logisticsEvents", events);
        
        return detail;
    }
    
    /**
     * 获取异常前状态，优先从 logistics_events 表中查找最后一个非异常状态
     */
    private String getPreExceptionStatus(Long orderId) {
        LogisticsEvent lastEvent = logisticsEventMapper.selectLastNonExceptionStatusByOrderId(orderId);
        if (lastEvent != null && lastEvent.getStatus() != null) {
            return lastEvent.getStatus();
        }
        
        // 后备方案：从配送任务表中推断
        DeliveryTask task = deliveryTaskMapper.selectByOrderId(orderId);
        return inferPreExceptionStatus(task);
    }

    @Override
    @Transactional
    public void compensateOrder(Long orderId, BigDecimal amount, String reason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        
        recordLogisticsEvent(orderId, order.getStatus(), 
            "平台已为您发放补偿", null);
        log.info("订单补偿完成, orderId={}, amount={}", orderId, amount);
    }
}
