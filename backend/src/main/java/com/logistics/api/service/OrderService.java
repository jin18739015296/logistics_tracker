package com.logistics.api.service;

import com.logistics.api.dto.CreateOrderRequest;
import com.logistics.api.dto.OrderDTO;
import com.logistics.api.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OrderService {

    Order createOrder(CreateOrderRequest request, String username);

    List<OrderDTO> getUserOrders(String username);

    OrderDTO getOrderDetail(Long orderId, String username);

    /**
     * 按业务订单号查询详情（权限校验同 {@link #getOrderDetail(Long, String)}）。
     * 供 App 物流查询等场景使用，避免用户在输入框中猜测主键 ID。
     */
    OrderDTO getOrderDetailByOrderNo(String orderNo, String username);

    /**
     * 仅订单相关方可查看该单的物流/轨迹：下单用户、订单上的配送员、任务上的配送员、admin。
     */
    void checkOrderViewPermission(String username, Long orderId);

    Page<OrderDTO> getOrdersWithFilter(String status, String keyword, String startDate, String endDate, Pageable pageable);

    OrderDTO getOrderDetailById(Long id);

    Map<String, Object> getOrderStatistics();

    Map<String, Object> getTodayOrderStats();

    List<Map<String, Object>> getOrderTrend(LocalDateTime start, LocalDateTime end, String groupBy);

    Map<String, Long> getOrderStatusDistribution();

    BigDecimal getTotalRevenue(LocalDateTime start, LocalDateTime end);

    List<Map<String, Object>> getRevenueTrend(LocalDateTime start, LocalDateTime end);

    List<OrderDTO> getOrdersByStatus(String status);

    Map<String, Object> getExceptionOrders(String type, int page, int size);



    void processRefund(Long orderId, String refundType, BigDecimal amount, String reason);

    Map<String, Object> searchOrders(String keyword, int page, int size);

    Map<String, Object> getOrderFullDetail(Long orderId);

    void compensateOrder(Long orderId, BigDecimal amount, String reason);

    /** 定时任务：按配置将符合条件的订单自动标记为 exception */
    void runAutoExceptionRules();

/**
     * 管理员按业务动作恢复异常订单。
     * @param orderId 订单ID
     * @param action 动作：continue-继续配送 / reassign-改派 / cancel-取消订单
     * @param remark 备注
     */
    void adminRecoverException(Long orderId, String action, String remark);
}
