package com.logistics.api.service;

import com.logistics.api.model.CourierReview;

import java.util.List;
import java.util.Map;

public interface CourierReviewService {

    /**
     * 提交评价
     */
    CourierReview submitReview(CourierReview review);

    /**
     * 获取评价详情
     */
    CourierReview getReviewById(Long id);

    /**
     * 获取订单的评价
     */
    CourierReview getReviewByOrderId(Long orderId);

    /**
     * 获取配送员的评价列表
     */
    List<CourierReview> getReviewsByCourierId(Long courierId, int page, int size);

    /**
     * 获取配送员平均评分
     */
    Double getAverageRating(Long courierId);

    /**
     * 获取配送员评价数量
     */
    int getReviewCount(Long courierId);

    /**
     * 获取评分分布
     */
    List<Map<String, Object>> getRatingDistribution(Long courierId);

    /**
     * 检查订单是否已评价
     */
    boolean hasReviewed(Long orderId);

    /**
     * 查询指定用户对指定订单的评价
     */
    CourierReview getReviewByOrderIdAndUserId(Long orderId, Long userId);
}
