package com.logistics.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResult {
    private Long orderId;
    private String paymentNo;
    private String status;
    private BigDecimal amount;
    private LocalDateTime payTime;
    private String message;
}
