package com.logistics.api.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchResult {
    private Long orderId;
    private Long courierId;
    private String courierName;
    private String courierPhone;
    private Double distance;
    private String status;
    private String message;
}
