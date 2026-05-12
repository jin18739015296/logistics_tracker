package com.logistics.api.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackingRecord {
    private Long id;
    private Long orderId;
    private Long courierId;
    private String deviceId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String location;
    private Double temperature;
    private Double humidity;
    private String status;
    private LocalDateTime createTime;
}
