package com.logistics.api.controller.admin;

import com.logistics.api.common.Result;
import com.logistics.api.dto.OrderDTO;
import com.logistics.api.service.GrabOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员异常订单处理Controller
 * 处理已支付但未进入抢单池的异常订单
 */
@Slf4j
@RestController
@RequestMapping("/admin/exception-orders")
@PreAuthorize("hasAnyRole('ADMIN', 'admin')")
public class AdminExceptionOrderController {

    @Autowired
    private GrabOrderService grabOrderService;



    /**
     * 将单个订单加入抢单池
     */
    @PostMapping("/{orderId}/add-to-pool")
    public ResponseEntity<Result<String>> addToGrabPool(@PathVariable Long orderId) {
        try {
            grabOrderService.addToGrabPool(orderId);
            log.info("管理员将订单加入抢单池: orderId={}", orderId);
            return ResponseEntity.ok(Result.success("订单已加入抢单池"));
        } catch (Exception e) {
            log.error("加入抢单池失败, orderId={}", orderId, e);
            return ResponseEntity.badRequest()
                    .body(Result.error(100004, "操作失败: " + e.getMessage()));
        }
    }

    /**
     * 获取抢单池统计信息
     */
    @GetMapping("/pool-stats")
    public ResponseEntity<Result<Map<String, Object>>> getGrabPoolStats() {
        try {
            int poolSize = grabOrderService.getGrabPoolSize();
            List<OrderDTO> exceptionOrders = grabOrderService.getPaidOrdersNotInGrabPool();
            
            Map<String, Object> stats = Map.of(
                "grabPoolSize", poolSize,
                "exceptionOrderCount", exceptionOrders.size()
            );
            
            return ResponseEntity.ok(Result.success(stats));
        } catch (Exception e) {
            log.error("获取抢单池统计失败", e);
            return ResponseEntity.badRequest()
                    .body(Result.error(100005, "查询失败: " + e.getMessage()));
        }
    }
}
