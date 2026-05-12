package com.logistics.api.service;

import com.logistics.api.dto.OrderDTO;

import java.util.List;

public interface GrabOrderService {

    void addToGrabPool(Long orderId);

    void removeFromGrabPool(Long orderId);

    List<OrderDTO> getGrabableOrders(String city, Double lat, Double lng, int limit);

    String tryGrabOrder(Long orderId, Long courierId);

    boolean isInGrabPool(Long orderId);

    int getGrabPoolSize();

    /**
     * 获取所有抢单池中的订单ID
     */
    List<Long> getAllGrabPoolOrderIds();

    /**
     * 查询已支付但未在抢单池中的订单（异常订单）
     */
    List<OrderDTO> getPaidOrdersNotInGrabPool();

    /**
     * 批量将订单加入抢单池
     */
    int batchAddToGrabPool(List<Long> orderIds);
}
