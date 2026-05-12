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
public class RealTimeLocationDTO {
    private Long orderId;
    private Long courierId;
    private String courierName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDateTime timestamp;
    private String type;
    
    // 新增字段：用于轨迹平滑/朝向展示
    private Double speed;              // 当前速度 (m/s)
    private Double direction;          // 车头方向角 (0-360度)
}
