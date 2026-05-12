package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.dto.PaymentRequest;
import com.logistics.api.dto.PaymentResult;
import com.logistics.api.service.PaymentService;
import com.logistics.api.service.WalletService;
import com.logistics.api.service.UserService;
import com.logistics.api.model.Order;
import com.logistics.api.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderMapper orderMapper;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create")
    public ResponseEntity<Result<PaymentResult>> createPayment(@RequestBody PaymentRequest request, Principal principal) {
        PaymentResult result = paymentService.createPayment(request.getOrderId(), request.getPayMethod());
        return ResponseEntity.ok(Result.success(result));
    }

    @PostMapping("/mock/{orderId}")
    public ResponseEntity<Result<PaymentResult>> mockPayment(@PathVariable Long orderId, Principal principal) {
        PaymentResult result = paymentService.mockPayment(orderId);
        return ResponseEntity.ok(Result.success("支付成功", result));
    }

    @GetMapping("/status/{orderId}")
    public ResponseEntity<Result<PaymentResult>> queryPaymentStatus(@PathVariable Long orderId, Principal principal) {
        PaymentResult result = paymentService.queryPaymentStatus(orderId);
        return ResponseEntity.ok(Result.success(result));
    }

    @PostMapping("/callback")
    public ResponseEntity<Result<Void>> handleCallback(@RequestBody Map<String, String> callback) {
        String paymentNo = callback.get("paymentNo");
        String status = callback.get("status");
        String transactionId = callback.get("transactionId");
        
        paymentService.handlePaymentCallback(paymentNo, status, transactionId);
        return ResponseEntity.ok(Result.success());
    }

    @PostMapping("/balance/{orderId}")
    public ResponseEntity<Result<PaymentResult>> balancePayment(@PathVariable Long orderId, Principal principal) {
        Long userId = userService.getUserByUsername(principal.getName()).getId();
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return ResponseEntity.badRequest().body(Result.error(30001, "订单不存在"));
        }
        if (!order.getUserId().equals(userId)) {
            return ResponseEntity.badRequest().body(Result.error(30006, "无权操作此订单"));
        }

        // 先创建支付记录
        PaymentResult result = paymentService.createPayment(orderId, "balance");

        // 从钱包扣款
        walletService.deductPayment(userId, order.getTotalAmount(), orderId, order.getOrderNo());

        // 标记支付成功
        paymentService.handlePaymentSuccess(orderId);

        result.setStatus("SUCCESS");
        result.setMessage("余额支付成功");
        return ResponseEntity.ok(Result.success("余额支付成功", result));
    }
}
