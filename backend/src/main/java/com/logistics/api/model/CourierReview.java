package com.logistics.api.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 配送员评价
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourierReview {
    private Long id;
    private Long courierId;
    /** 关联订单主键（API 返回；界面展示订单号请用 orderNo） */
    private Long orderId;
    /** 订单业务单号 */
    private String orderNo;
    private Long userId;
    
    /**
     * 评分 1-5
     */
    private Integer rating;
    
    /**
     * 评价内容
     */
    private String content;
    
    /**
     * 评价标签，逗号分隔
     */
    private String tags;
    
    private LocalDateTime createTime;
}
