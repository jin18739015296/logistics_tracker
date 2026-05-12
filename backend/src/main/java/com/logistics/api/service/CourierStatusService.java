package com.logistics.api.service;

import com.logistics.api.dto.CourierDistanceInfo;
import com.logistics.api.model.CourierStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 配送员状态服务
 * 管理配送员的上线、下线、位置更新等状态
 */
public interface CourierStatusService {

    /**
     * 配送员上线
     * @param courierId 配送员ID
     * @param latitude 初始纬度
     * @param longitude 初始经度
     * @param workCity 工作城市
     */
    void goOnline(Long courierId, Double latitude, Double longitude, String workCity);

    /**
     * 配送员下线
     * @param courierId 配送员ID
     */
    void goOffline(Long courierId);

    /**
     * 更新配送员位置（空闲时调用）
     * @param courierId 配送员ID
     * @param latitude 纬度
     * @param longitude 经度
     */
    void updateLocation(Long courierId, Double latitude, Double longitude);

    /**
     * 开始配送（状态变为忙碌）
     * @param courierId 配送员ID
     * @param orderId 订单ID；可为 null，仅将配送员置为 busy 并从可派单池移除（批量开始配送场景需随后调用 {@link #syncActiveOrderKeysFromDb}）
     */
    void startDelivery(Long courierId, Long orderId);

    /**
     * 将订单加入配送员的订单集合（仅增加计数，不改变状态）。
     * 用于揽件等场景：配送员身上有订单但仍可继续接单（idle）。
     * @param courierId 配送员ID
     * @param orderId 订单ID
     */
    void addOrderToCourier(Long courierId, Long orderId);

    /**
     * 根据数据库中的进行中配送任务，重建 Redis 中的 {@code courier:orders}、{@code courier:order_count}。
     * 批量开始配送等与单订单 {@link #startDelivery(Long, Long)} 分支配合使用，保证计数与集合一致。
     */
    void syncActiveOrderKeysFromDb(Long courierId);

    /**
     * 完成配送（状态变回空闲）
     * @param courierId 配送员ID
     * @param orderId 订单ID
     */
    void finishDelivery(Long courierId, Long orderId);

    /**
     * 检查配送员是否可分配订单
     * @param courierId 配送员ID
     * @return true-可以分配
     */
    boolean isAvailableForDispatch(Long courierId);

    /**
     * 获取配送员当前位置
     * @param courierId 配送员ID
     * @return 位置字符串 "lat,lng"，不存在返回null
     */
    String getCourierLocation(Long courierId);

    /**
     * 获取所有可分配的配送员ID列表
     * @param city 城市（可选筛选）
     * @return 配送员ID列表
     */
    List<Long> getAvailableCourierIds(String city);

    /**
     * 清理长时间未更新的配送员状态（自动离线）
     * 应由定时任务调用
     */
    void cleanupInactiveCouriers();

    /**
     * 获取配送员当前配送中的订单数量
     * @param courierId 配送员ID
     * @return 订单数量
     */
    int getCurrentOrderCount(Long courierId);

    /**
     * 检查配送员是否还可以接单（未达上限）
     * @param courierId 配送员ID
     * @param maxOrders 最大接单数
     * @return true-还可以接单
     */
    boolean canAcceptMoreOrders(Long courierId, int maxOrders);

    /**
     * 获取配送员当前所有配送中的订单ID列表
     * @param courierId 配送员ID
     * @return 订单ID列表
     */
    List<Long> getCurrentOrderIds(Long courierId);

    /**
     * 获取配送员状态（从数据库，用于前端展示）
     * @param courierId 配送员ID
     * @return 配送员状态对象，不存在返回null
     */
    CourierStatus getCourierStatusFromDB(Long courierId);

    /**
     * 获取配送员当前完整状态（数据库为主，Redis为辅）
     * 用于配送员首页状态查询，会自动同步Redis
     * @param courierId 配送员ID
     * @param latitude 前端传入的当前纬度（可选，用于更新位置）
     * @param longitude 前端传入的当前经度（可选，用于更新位置）
     * @return 配送员状态信息Map
     */
    Map<String, Object> getCourierCurrentStatus(Long courierId, Double latitude, Double longitude);

    /**
     * 获取所有配送员状态（从数据库）
     * @return 配送员状态列表
     */
    List<com.logistics.api.model.CourierStatus> getAllCourierStatusFromDB();

    /**
     * 获取指定范围内可分配的配送员ID列表
     * @param latitude 中心纬度
     * @param longitude 中心经度
     * @param radiusKm 半径（公里）
     * @return 配送员ID列表
     */
    List<Long> getAvailableCourierIdsWithinRadius(Double latitude, Double longitude, Double radiusKm);

    /**
     * 使用Redis Geo获取指定城市范围内可分配的配送员ID列表
     * 性能优于全量扫描，适合配送员数量多的场景
     * @param city 城市名称
     * @param latitude 中心纬度
     * @param longitude 中心经度
     * @param radiusKm 半径（公里）
     * @return 配送员ID列表
     */
    List<Long> getAvailableCourierIdsWithinRadiusByCity(String city, Double latitude, Double longitude, Double radiusKm);

    /**
     * 获取指定城市范围内可分配的配送员及其距离信息
     * 按距离排序，包含当前订单数
     * @param city 城市名称
     * @param latitude 中心纬度
     * @param longitude 中心经度
     * @param radiusKm 半径（公里）
     * @param limit 限制返回数量（可选，默认50）
     * @return 配送员距离信息列表
     */
    List<CourierDistanceInfo> getAvailableCouriersWithDistance(String city, Double latitude, Double longitude, Double radiusKm, Integer limit);
}
