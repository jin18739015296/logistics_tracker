package com.logistics.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationUploadRequest {
    private Long orderId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal accuracy;
    private String location;
    
    // 新增字段：用于轨迹平滑与ETA展示
    private Double speed;              // 当前速度 (m/s)
    private Double direction;          // 车头方向角 (0-360度)
}
