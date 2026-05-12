package com.logistics.api.service;

import com.logistics.api.dto.CourierTaskDTO;
import com.logistics.api.model.LogisticsEvent;

import java.math.BigDecimal;
import java.util.List;

/**
 * 配送员订单操作服务
 * 处理配送员对订单的状态更新操作
 */
public interface CourierOrderService {

    /**
     * 配送员揽件
     * 更新订单状态为已揽件，记录物流事件
     * @param orderId 订单ID
     * @param courierId 配送员ID
     * @param location 揽件地点
     * @param latitude 纬度
     * @param longitude 经度
     */
    void pickupOrder(Long orderId, Long courierId, String location, BigDecimal latitude, BigDecimal longitude);

    /**
     * 更新运输状态
     * 记录运输中的物流事件
     * @param orderId 订单ID
     * @param courierId 配送员ID
     * @param location 当前位置
     * @param description 描述
     */
    void updateInTransit(Long orderId, Long courierId, String location, String description);

    /**
     * 开始运输
     * 更新订单状态为运输中
     * @param orderId 订单ID
     * @param courierId 配送员ID
     * @param location 运输地点
     */
    void startDelivery(Long orderId, Long courierId, String location);

    /**
     * 根据订单号揽件
     * @param orderNo 订单号
     * @param courierId 配送员ID
     */
    void pickupOrderByNo(String orderNo, Long courierId);

    /**
     * 确认送达
     * 更新订单状态为已送达，完成配送任务
     * @param orderId 订单ID
     * @param courierId 配送员ID
     * @param location 送达地点
     * @param latitude 纬度
     * @param longitude 经度
     */
    void confirmDelivery(Long orderId, Long courierId, String location, 
                        BigDecimal latitude, BigDecimal longitude);

    /**
     * 获取配送员的当前任务
     * @param courierId 配送员ID
     * @return 配送任务DTO列表
     */
    List<CourierTaskDTO> getCurrentTasks(Long courierId);

    /**
     * 获取配送员待确认的订单
     * @param courierId 配送员ID
     * @return 待确认的配送任务DTO列表
     */
    List<CourierTaskDTO> getPendingConfirmTasks(Long courierId);

    /**
     * 获取订单的物流事件历史
     * @param orderId 订单ID
     * @return 物流事件列表
     */
    List<LogisticsEvent> getOrderEvents(Long orderId);

    /**
     * 上报异常
     * 记录配送过程中的异常情况
     * @param orderId 订单ID
     * @param courierId 配送员ID
     * @param exceptionType 异常类型
     * @param description 异常描述
     */
    void reportException(Long orderId, Long courierId, String exceptionType, String description);

    boolean confirmOrder(Long orderId, Long courierId);

    boolean rejectOrder(Long orderId, Long courierId, String reason);

    /**
     * 开始配送
     * 将配送员状态设为busy，所有已揽件订单同时变为运输中
     * @param courierId 配送员ID
     */
    void startBatchDelivery(Long courierId);
}
