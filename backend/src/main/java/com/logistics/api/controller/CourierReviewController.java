package com.logistics.api.controller;

import com.logistics.api.common.Result;
import com.logistics.api.model.CourierReview;
import com.logistics.api.model.User;
import com.logistics.api.service.CourierReviewService;
import com.logistics.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/courier-reviews")
public class CourierReviewController {

    @Autowired
    private CourierReviewService courierReviewService;

    @Autowired
    private UserService userService;

    /**
     * 提交评价
     */
    @PostMapping
    public ResponseEntity<Result<CourierReview>> submitReview(@RequestBody CourierReview review, Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        review.setUserId(user.getId());
        CourierReview result = courierReviewService.submitReview(review);
        return ResponseEntity.ok(Result.success("评价提交成功", result));
    }

    /**
     * 获取评价详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Result<CourierReview>> getReviewDetail(@PathVariable Long id) {
        CourierReview review = courierReviewService.getReviewById(id);
        return ResponseEntity.ok(Result.success(review));
    }

    /**
     * 获取订单的评价
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<Result<CourierReview>> getOrderReview(@PathVariable Long orderId) {
        CourierReview review = courierReviewService.getReviewByOrderId(orderId);
        return ResponseEntity.ok(Result.success(review));
    }

    /**
     * 检查当前用户是否已对该订单评价
     */
    @GetMapping("/order/{orderId}/my")
    public ResponseEntity<Result<CourierReview>> getMyReviewByOrderId(@PathVariable Long orderId, Principal principal) {
        User user = userService.getUserByUsername(principal.getName());
        CourierReview review = courierReviewService.getReviewByOrderIdAndUserId(orderId, user.getId());
        return ResponseEntity.ok(Result.success(review));
    }

    /**
     * 获取配送员的评价列表
     */
    @GetMapping("/courier/{courierId}")
    public ResponseEntity<Result<List<CourierReview>>> getCourierReviews(
            @PathVariable Long courierId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<CourierReview> reviews = courierReviewService.getReviewsByCourierId(courierId, page, size);
        return ResponseEntity.ok(Result.success(reviews));
    }

    /**
     * 获取配送员评分统计
     */
    @GetMapping("/courier/{courierId}/stats")
    public ResponseEntity<Result<Map<String, Object>>> getCourierReviewStats(@PathVariable Long courierId) {
        Double averageRating = courierReviewService.getAverageRating(courierId);
        int reviewCount = courierReviewService.getReviewCount(courierId);
        List<Map<String, Object>> distribution = courierReviewService.getRatingDistribution(courierId);

        Map<String, Object> result = new HashMap<>();
        result.put("averageRating", averageRating);
        result.put("reviewCount", reviewCount);
        result.put("distribution", distribution);

        return ResponseEntity.ok(Result.success(result));
    }
}
