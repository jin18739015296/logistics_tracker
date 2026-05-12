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
public class TrackPointDTO {
    private Long id;
    private Long orderId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal accuracy;
    private LocalDateTime createTime;
}
