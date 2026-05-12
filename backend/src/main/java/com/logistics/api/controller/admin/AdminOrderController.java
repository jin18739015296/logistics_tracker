package com.logistics.api.controller.admin;

import com.logistics.api.common.Result;
import com.logistics.api.dto.OrderDTO;
import com.logistics.api.enums.OrderExceptionType;
import com.logistics.api.model.User;
import com.logistics.api.service.DispatchService;
import com.logistics.api.service.GrabOrderService;
import com.logistics.api.service.OrderService;
import com.logistics.api.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/admin/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'admin')")
public class AdminOrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private UserService userService;

    @Autowired
    private GrabOrderService grabOrderService;

    /**
     * 与「抢单调度」相关的待办：已支付、尚未分配配送员、且当前不在抢单池中的订单。
     * 管理员可对此类单执行「加入抢单池」或「自动分配」；与业务上「全部已支付单」不同。
     */
    @GetMapping("/pending-dispatch")
    public ResponseEntity<Result<Map<String, Object>>> getPendingDispatchOrders() {
        List<OrderDTO> orders = grabOrderService.getPaidOrdersNotInGrabPool();

        Map<String, Object> result = new HashMap<>();
        result.put("orders", orders);
        result.put("total", orders.size());

        return ResponseEntity.ok(Result.success(result));
    }

    @PostMapping("/{orderId}/auto-dispatch")
    public ResponseEntity<Result<Map<String, Object>>> autoDispatchOrder(@PathVariable Long orderId) {
        try {
            User courier = dispatchService.autoAssignCourier(orderId);

            if (courier == null) {
                log.warn("自动分配无可用配送员: orderId={}", orderId);
                return ResponseEntity.badRequest()
                        .body(Result.error(30001,
                                "暂无可用配送员（可能均无在线/已满单/不在服务范围）。请将订单加入抢单池或稍后再试。"));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("orderId", orderId);
            result.put("assigned", Boolean.TRUE);
            result.put("courierId", courier.getId());
            result.put("courierName", courier.getName());
            result.put("courierPhone", courier.getPhone());

            return ResponseEntity.ok(Result.success("自动分配成功", result));
        } catch (Exception e) {
            log.error("自动分配失败: orderId={}", orderId, e);
            return ResponseEntity.badRequest().body(Result.error(30001, "分配失败: " + e.getMessage()));
        }
    }



    @GetMapping("/exceptions")
    public ResponseEntity<Result<Map<String, Object>>> getExceptionOrders(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Map<String, Object> result = orderService.getExceptionOrders(type, page, size);
        return ResponseEntity.ok(Result.success(result));
    }

/**
     * 按业务动作恢复异常订单（推荐）。
     * action: continue-继续配送 / reassign-改派 / cancel-取消订单
     */
    @PostMapping("/{orderId}/recover-exception")
    public ResponseEntity<Result<String>> recoverOrderException(
            @PathVariable Long orderId,
            @RequestBody(required = false) Map<String, String> body) {
        try {
            String action = body != null ? body.get("action") : null;
            String remark = body != null ? body.get("remark") : null;
            orderService.adminRecoverException(orderId, action, remark);
            return ResponseEntity.ok(Result.success("已恢复异常订单"));
        } catch (Exception e) {
            log.error("恢复异常订单失败: orderId={}", orderId, e);
            return ResponseEntity.badRequest().body(Result.error(30007, e.getMessage()));
        }
    }



    @PostMapping("/{orderId}/refund")
    public ResponseEntity<Result<String>> processRefund(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> request) {
        try {
            String refundType = (String) request.get("refundType");
            java.math.BigDecimal amount = request.get("amount") != null ? 
                    new java.math.BigDecimal(request.get("amount").toString()) : null;
            String reason = (String) request.get("reason");
            
            orderService.processRefund(orderId, refundType, amount, reason);
            return ResponseEntity.ok(Result.success("退款处理成功"));
        } catch (Exception e) {
            log.error("退款处理失败: orderId={}", orderId, e);
            return ResponseEntity.badRequest().body(Result.error(30004, e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Result<Map<String, Object>>> searchOrders(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Map<String, Object> result = orderService.searchOrders(keyword, page, size);
        return ResponseEntity.ok(Result.success(result));
    }

    @GetMapping("/{orderId}/detail")
    public ResponseEntity<Result<Map<String, Object>>> getOrderDetail(@PathVariable Long orderId) {
        Map<String, Object> detail = orderService.getOrderFullDetail(orderId);
        if (detail == null) {
            return ResponseEntity.status(404).body(Result.error(30005, "订单不存在"));
        }
        return ResponseEntity.ok(Result.success(detail));
    }


}
