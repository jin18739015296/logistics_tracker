package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.dto.CreateOrderRequest;
import com.logistics.api.dto.OrderDTO;
import com.logistics.api.model.LogisticsEvent;
import com.logistics.api.model.Order;
import com.logistics.api.model.User;
import com.logistics.api.service.OrderService;
import com.logistics.api.service.UserOrderService;
import com.logistics.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    @Autowired
    private UserOrderService userOrderService;

    @Autowired
    private UserService userService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Result<Order>> createOrder(@RequestBody CreateOrderRequest request, Principal principal) {
        String username = principal.getName();
        Order order = orderService.createOrder(request, username);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Result.success("订单创建成功", order));
    }

    @GetMapping("/my")
    public ResponseEntity<Result<List<OrderDTO>>> getMyOrders(Principal principal) {
        String username = principal.getName();
        List<OrderDTO> orders = orderService.getUserOrders(username);
        return ResponseEntity.ok(Result.success(orders));
    }

    /**
     * 当前登录用户按业务订单号查询订单详情（物流查询用 query，避免部分环境下长路径被当成静态资源）。
     */
    @GetMapping("/my/detail-by-order-no")
    public ResponseEntity<Result<OrderDTO>> getMyOrderByOrderNo(
            @RequestParam("orderNo") String orderNo,
            Principal principal) {
        String username = principal.getName();
        OrderDTO detail = orderService.getOrderDetailByOrderNo(orderNo, username);
        return ResponseEntity.ok(Result.success(detail));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Result<OrderDTO>> getOrderDetail(@PathVariable Long id, Principal principal) {
        String username = principal.getName();
        OrderDTO detail = orderService.getOrderDetail(id, username);
        return ResponseEntity.ok(Result.success(detail));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Result<String>> cancelOrder(@PathVariable Long id, 
                                         @RequestBody(required = false) Map<String, String> request,
                                         Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        String reason = request != null ? request.get("reason") : null;
        userOrderService.cancelOrder(id, user.getId(), reason);
        return ResponseEntity.ok(Result.success("订单取消成功"));
    }

    @PostMapping("/{id}/confirm-received")
    public ResponseEntity<Result<String>> confirmReceived(@PathVariable Long id, Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        userOrderService.confirmReceived(id, user.getId());
        return ResponseEntity.ok(Result.success("确认收货成功"));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<Result<String>> reviewOrder(@PathVariable Long id,
                                         @RequestBody Map<String, Object> request,
                                         Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        Object ratingObj = request.get("rating");
        Integer rating = ratingObj == null ? null : ((Number) ratingObj).intValue();
        String content = (String) request.get("content");
        String tags = (String) request.get("tags");
        userOrderService.reviewOrder(id, user.getId(), rating, content, tags);
        return ResponseEntity.ok(Result.success("评价成功"));
    }

    @GetMapping("/{id}/events")
    public ResponseEntity<Result<List<LogisticsEvent>>> getOrderEvents(@PathVariable Long id, Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        List<LogisticsEvent> events = userOrderService.getOrderEvents(id, user.getId());
        return ResponseEntity.ok(Result.success(events));
    }
}
