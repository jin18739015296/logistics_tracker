package com.logistics.api.service.impl;

import com.logistics.api.common.BusinessException;
import com.logistics.api.common.ErrorCode;
import com.logistics.api.mapper.CourierReviewMapper;
import com.logistics.api.mapper.OrderMapper;
import com.logistics.api.model.CourierReview;
import com.logistics.api.model.Order;
import com.logistics.api.service.CourierReviewService;
import com.logistics.api.service.NotificationService;
import com.logistics.api.service.support.NotificationCopy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CourierReviewServiceImpl implements CourierReviewService {

    @Autowired
    private CourierReviewMapper courierReviewMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private NotificationService notificationService;

    @Override
    public CourierReview submitReview(CourierReview review) {
        // 检查该用户是否已对该订单评价过
        CourierReview existing = courierReviewMapper.selectByOrderIdAndUserId(review.getOrderId(), review.getUserId());
        if (existing != null) {
            throw new BusinessException(ErrorCode.ALREADY_EXISTS, "您已评价过该订单");
        }

        courierReviewMapper.insert(review);

        Order order = orderMapper.selectById(review.getOrderId());
        if (order != null) {
            review.setOrderNo(order.getOrderNo());
        }

        // 通知配送员
        notificationService.sendNotification(
                review.getCourierId(),
                "courier",
                "system",
                NotificationCopy.userReviewCourierTitle(),
                NotificationCopy.courierReviewReceivedBody(
                        order != null ? order.getOrderNo() : null,
                        review.getRating() != null ? review.getRating() : 0)
        );

        log.info("提交评价成功, orderId: {}, courierId: {}, rating: {}",
                review.getOrderId(), review.getCourierId(), review.getRating());
        return review;
    }

    @Override
    public CourierReview getReviewById(Long id) {
        CourierReview review = courierReviewMapper.selectById(id);
        if (review == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "评价不存在");
        }
        enrichOrderNo(review);
        return review;
    }

    @Override
    public CourierReview getReviewByOrderId(Long orderId) {
        CourierReview review = courierReviewMapper.selectByOrderId(orderId);
        if (review != null) {
            enrichOrderNo(review);
        }
        return review;
    }

    @Override
    public List<CourierReview> getReviewsByCourierId(Long courierId, int page, int size) {
        int offset = (page - 1) * size;
        List<CourierReview> list = courierReviewMapper.selectByCourierId(courierId, offset, size);
        enrichOrderNos(list);
        return list;
    }

    @Override
    public Double getAverageRating(Long courierId) {
        Double avg = courierReviewMapper.selectAverageRatingByCourierId(courierId);
        return avg != null ? avg : 0.0;
    }

    @Override
    public int getReviewCount(Long courierId) {
        return courierReviewMapper.selectCountByCourierId(courierId);
    }

    @Override
    public List<Map<String, Object>> getRatingDistribution(Long courierId) {
        return courierReviewMapper.selectRatingDistribution(courierId);
    }

    @Override
    public boolean hasReviewed(Long orderId) {
        return courierReviewMapper.selectByOrderId(orderId) != null;
    }

    @Override
    public CourierReview getReviewByOrderIdAndUserId(Long orderId, Long userId) {
        if (orderId == null || userId == null) {
            return null;
        }
        CourierReview review = courierReviewMapper.selectByOrderIdAndUserId(orderId, userId);
        if (review != null) {
            enrichOrderNo(review);
        }
        return review;
    }

    private void enrichOrderNos(List<CourierReview> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> ids = list.stream()
                .map(CourierReview::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return;
        }
        List<Order> orders = orderMapper.selectByIds(ids);
        Map<Long, String> map = orders.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Order::getId, Order::getOrderNo, (a, b) -> a));
        for (CourierReview r : list) {
            if (r.getOrderId() != null) {
                r.setOrderNo(map.get(r.getOrderId()));
            }
        }
    }

    private void enrichOrderNo(CourierReview review) {
        if (review == null || review.getOrderId() == null) {
            return;
        }
        Order o = orderMapper.selectById(review.getOrderId());
        if (o != null) {
            review.setOrderNo(o.getOrderNo());
        }
    }
}
