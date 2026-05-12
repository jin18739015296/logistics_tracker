package com.logistics.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 配送员距离信息DTO
 * 用于返回配送员及其与某点的距离
 */
@Data
public class CourierDistanceInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 配送员ID
     */
    private Long courierId;

    /**
     * 距离（公里）
     */
    private Double distanceKm;

    /**
     * 当前订单数
     */
    private Integer currentOrderCount;

    /**
     * 配送员姓名（可选，用于展示）
     */
    private String courierName;

    /**
     * 配送员电话（可选，用于展示）
     */
    private String courierPhone;
}
