package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderAddress {
    private Long id;
    private Long orderId;
    private String type; // sender 或 receiver
    private String contactName;
    private String contactPhone;
    private String province;
    private String city;
    private String district;
    private String detailAddress;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDateTime createTime;
}
