package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRecord {
    private Long id;
    private String paymentNo;
    private Long orderId;
    
    private String payMethod;
    private BigDecimal amount;
    private String status;
    
    private String payChannel;
    private String transactionId;
    
    private LocalDateTime payTime;
    private LocalDateTime createTime;
}
