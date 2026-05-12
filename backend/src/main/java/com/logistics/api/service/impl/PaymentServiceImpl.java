package com.logistics.api.service.impl;

import com.logistics.api.common.BusinessException;
import com.logistics.api.common.ErrorCode;
import com.logistics.api.dto.PaymentResult;
import com.logistics.api.enums.OrderStatus;
import com.logistics.api.enums.PaymentStatus;
import com.logistics.api.mapper.OrderMapper;
import com.logistics.api.mapper.PaymentRecordMapper;
import com.logistics.api.messaging.MQMessageSender;
import com.logistics.api.model.Order;
import com.logistics.api.model.PaymentRecord;
import com.logistics.api.service.LogisticsEventService;
import com.logistics.api.service.NotificationService;
import com.logistics.api.service.support.NotificationCopy;
import com.logistics.api.service.PaymentService;
import com.logistics.api.service.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private OrderMapper orderMapper;
    
    @Autowired
    private PaymentRecordMapper paymentRecordMapper;
    
    @Autowired
    private MQMessageSender mqMessageSender;
    
    @Autowired
    private LogisticsEventService logisticsEventService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private WalletService walletService;

    private static final DateTimeFormatter PAYMENT_NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Random RANDOM = new Random();

    private String generatePaymentNo() {
        String prefix = "PAY";
        String dateStr = LocalDateTime.now().format(PAYMENT_NO_FORMATTER);
        String randomStr = String.format("%06d", RANDOM.nextInt(1000000));
        return prefix + dateStr + randomStr;
    }

    @Override
    @Transactional
    public PaymentResult createPayment(Long orderId, String payMethod) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        
        if (!OrderStatus.PENDING.getCode().equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单状态不正确，无法支付");
        }
        
        PaymentRecord record = new PaymentRecord();
        record.setPaymentNo(generatePaymentNo());
        record.setOrderId(orderId);
        record.setPayMethod(payMethod != null ? payMethod : "mock");
        record.setAmount(order.getTotalAmount());
        record.setStatus(PaymentStatus.PENDING.getCode());
        record.setCreateTime(LocalDateTime.now());
        
        paymentRecordMapper.insert(record);
        
        log.info("创建支付记录成功, paymentNo={}, orderId={}", record.getPaymentNo(), orderId);
        
        return PaymentResult.builder()
                .orderId(orderId)
                .paymentNo(record.getPaymentNo())
                .status(PaymentStatus.PENDING.getCode())
                .amount(order.getTotalAmount())
                .build();
    }

    @Override
    @Transactional
    public PaymentResult mockPayment(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        
        if (!OrderStatus.PENDING.getCode().equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单已支付或状态不正确");
        }
        
        PaymentRecord record = new PaymentRecord();
        record.setPaymentNo(generatePaymentNo());
        record.setOrderId(orderId);
        record.setPayMethod("mock");
        record.setAmount(order.getTotalAmount());
        record.setStatus(PaymentStatus.PROCESSING.getCode());
        record.setCreateTime(LocalDateTime.now());
        
        paymentRecordMapper.insert(record);
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        record.setStatus(PaymentStatus.SUCCESS.getCode());
        record.setPayTime(LocalDateTime.now());
        record.setTransactionId("MOCK_" + System.currentTimeMillis());
        paymentRecordMapper.updateStatus(record.getId(), PaymentStatus.SUCCESS.getCode(), record.getTransactionId());

        // 从用户钱包扣款
        try {
            walletService.deductPayment(order.getUserId(), order.getTotalAmount(), orderId, order.getOrderNo());
        } catch (Exception e) {
            log.warn("钱包扣款失败, orderId={}, err={}", orderId, e.getMessage());
            // 支付记录已成功，扣款失败不影响支付状态，可后续人工处理或异步重试
        }

        handlePaymentSuccess(orderId);
        
        log.info("模拟支付成功, orderId={}, amount={}", orderId, order.getTotalAmount());
        
        return PaymentResult.builder()
                .orderId(orderId)
                .paymentNo(record.getPaymentNo())
                .status(PaymentStatus.SUCCESS.getCode())
                .amount(order.getTotalAmount())
                .payTime(record.getPayTime())
                .message("支付成功")
                .build();
    }

    @Override
    @Transactional
    public void handlePaymentSuccess(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        
        orderMapper.updateStatus(orderId, OrderStatus.PAID.getCode());

        logisticsEventService.recordEvent(orderId, OrderStatus.PAID.getCode(), "订单支付成功", null);

        // 发送支付成功通知
        if (order != null) {
            notificationService.sendOrderNotification(
                    order.getUserId(),
                    "user",
                    NotificationCopy.paymentSuccessTitle(),
                    NotificationCopy.paymentSuccessBody(order.getOrderNo()),
                    orderId
            );
        }

        try {
            mqMessageSender.sendOrderPaidMessage(orderId);
            log.info("发送订单支付成功消息, orderId={}", orderId);
        } catch (Exception e) {
            log.error("发送订单支付消息失败, orderId={}", orderId, e);
        }
    }

    @Override
    public PaymentResult queryPaymentStatus(Long orderId) {
        PaymentRecord record = paymentRecordMapper.selectByOrderId(orderId);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "支付记录不存在");
        }
        
        return PaymentResult.builder()
                .orderId(orderId)
                .paymentNo(record.getPaymentNo())
                .status(record.getStatus())
                .amount(record.getAmount())
                .payTime(record.getPayTime())
                .build();
    }

    @Override
    @Transactional
    public void handlePaymentCallback(String paymentNo, String status, String transactionId) {
        PaymentRecord record = paymentRecordMapper.selectByPaymentNo(paymentNo);
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "支付记录不存在");
        }
        
        if (PaymentStatus.SUCCESS.getCode().equals(status)) {
            record.setStatus(PaymentStatus.SUCCESS.getCode());
            record.setTransactionId(transactionId);
            record.setPayTime(LocalDateTime.now());
            paymentRecordMapper.updateStatus(record.getId(), PaymentStatus.SUCCESS.getCode(), transactionId);
            
            handlePaymentSuccess(record.getOrderId());
            
            log.info("支付回调处理成功, paymentNo={}, orderId={}", paymentNo, record.getOrderId());
        } else if (PaymentStatus.FAILED.getCode().equals(status)) {
            record.setStatus(PaymentStatus.FAILED.getCode());
            paymentRecordMapper.updateStatus(record.getId(), PaymentStatus.FAILED.getCode(), null);
            
            log.warn("支付回调失败, paymentNo={}", paymentNo);
        }
    }
}
