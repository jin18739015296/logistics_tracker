package com.logistics.api.mapper;

import com.logistics.api.model.CourierStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourierStatusMapper {

    CourierStatus selectById(Long courierId);

    List<CourierStatus> selectByStatus(String status);

    long countByStatuses(@Param("statuses") List<String> statuses);

    int insert(CourierStatus status);

    int update(CourierStatus status);

    int updateLocation(@Param("courierId") Long courierId, @Param("lat") Double lat, @Param("lng") Double lng);

    int updateOrderCount(@Param("courierId") Long courierId, @Param("count") Integer count);

    /**
     * 仅下线：更新状态与订单数，不清空经纬度（保留「最后已知位置」供后台查看）
     */
    int updateOfflineState(@Param("courierId") Long courierId,
                           @Param("status") String status,
                           @Param("orderCount") Integer orderCount);

    int deleteById(Long courierId);
}
