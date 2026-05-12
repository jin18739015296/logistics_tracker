package com.logistics.api.service;

import com.logistics.api.dto.PaymentResult;

public interface PaymentService {
    
    PaymentResult createPayment(Long orderId, String payMethod);
    
    PaymentResult mockPayment(Long orderId);
    
    void handlePaymentSuccess(Long orderId);
    
    PaymentResult queryPaymentStatus(Long orderId);
    
    void handlePaymentCallback(String paymentNo, String status, String transactionId);
}
