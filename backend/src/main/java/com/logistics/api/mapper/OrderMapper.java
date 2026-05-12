package com.logistics.api.mapper;

import com.logistics.api.model.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {

    Order selectById(Long id);

    List<Order> selectByIds(@Param("ids") List<Long> ids);

    Order selectByOrderNo(String orderNo);

    List<Order> selectByUserId(Long userId);

    List<Order> selectByReceiverNormalizedPhone(@Param("normalizedPhone") String normalizedPhone);

    List<Order> selectByCourierId(Long courierId);

    List<Order> selectByStatus(String status);

    /**
     * 全表查询（非统计场景慎用；看板请用聚合方法）
     */
    List<Order> selectAll();

    long countTotalOrders();

    List<Map<String, Object>> selectOrderCountGroupByStatus();

    BigDecimal sumActualAmountByStatus(@Param("status") String status);

    long countByCreateTimeBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    long countByUpdateTimeBetweenAndStatuses(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statuses") List<String> statuses);

    BigDecimal sumActualByUpdateTimeBetweenAndStatuses(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statuses") List<String> statuses);

    long countWhereStatusNotIn(@Param("statuses") List<String> statuses);

    List<Map<String, Object>> selectCreateOrderTrendByDay(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    List<Map<String, Object>> selectCreateOrderTrendByHour(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    List<Map<String, Object>> selectRevenueTrendByUpdateDay(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    BigDecimal sumActualByCreateTimeBetweenAndStatuses(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("statuses") List<String> statuses);

    long countDistinctUsersByCreateTimeBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    long countByUserId(@Param("userId") Long userId);

    List<Map<String, Object>> selectOrderCountGroupByStatusForUser(@Param("userId") Long userId);

    BigDecimal sumActualByCourierCreateTimeAndStatus(
            @Param("courierId") Long courierId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("status") String status);

    BigDecimal sumActualByCourierUpdateTimeAndStatus(
            @Param("courierId") Long courierId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("status") String status);

    List<Order> selectPendingDispatch();
    
    List<Order> selectExceptionOrders();

    List<Order> selectByDateRange(@Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    long countSearchAdmin(@Param("keyword") String keyword);

    List<Order> searchAdminPaged(
            @Param("keyword") String keyword,
            @Param("offset") int offset,
            @Param("limit") int limit);

    int insert(Order order);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateActualAmount(@Param("id") Long id, @Param("actualAmount") BigDecimal actualAmount);

    int assignCourier(@Param("id") Long id, @Param("courierId") Long courierId);
    
    int updateDispatch(@Param("id") Long id, @Param("courierId") Long courierId, 
                       @Param("dispatchType") String dispatchType);
    
    int markException(@Param("id") Long id, @Param("exceptionType") String exceptionType);

    /** 解除异常标记（管理员将订单恢复为正常流转时调用） */
    int clearExceptionFlag(@Param("id") Long id);
    
    int clearCourier(@Param("id") Long id);
}
