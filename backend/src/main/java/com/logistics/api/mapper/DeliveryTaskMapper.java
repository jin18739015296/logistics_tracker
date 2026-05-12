package com.logistics.api.mapper;

import com.logistics.api.model.DeliveryTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface DeliveryTaskMapper {

    DeliveryTask selectById(Long id);

    DeliveryTask selectByOrderId(Long orderId);

    List<DeliveryTask> selectAllByOrderId(Long orderId);

    DeliveryTask selectLatestByOrderId(Long orderId);

    DeliveryTask selectByCourierIdAndOrderId(@Param("courierId") Long courierId, @Param("orderId") Long orderId);

    List<DeliveryTask> selectByCourierId(Long courierId);

    List<DeliveryTask> selectByCourierIdAndStatus(@Param("courierId") Long courierId, @Param("status") String status);

    List<DeliveryTask> selectByCourierIdAndStatuses(@Param("courierId") Long courierId, @Param("statuses") List<String> statuses);

    /** 批量：多名配送员在送任务，用于派单顺路批量算分 */
    List<DeliveryTask> selectByCourierIdsAndStatuses(@Param("courierIds") List<Long> courierIds,
                                                    @Param("statuses") List<String> statuses);

    List<DeliveryTask> selectAll();

    long countTotalTasks();

    long countByStatus(@Param("status") String status);

    long countDeliveredByDeliveryTimeBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    List<Map<String, Object>> selectTopCouriersByDeliveredCount(@Param("limit") int limit);

    List<DeliveryTask> selectByCourierIdAndCreateTimeBetween(
            @Param("courierId") Long courierId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    
    List<DeliveryTask> selectByStatus(String status);

    int insert(DeliveryTask task);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateAcceptTime(Long id);

    int updatePickupTime(Long id);

    int updateDeliveryTime(Long id);
}
