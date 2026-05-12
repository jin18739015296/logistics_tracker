package com.logistics.api.mapper;

import com.logistics.api.model.LogisticsEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LogisticsEventMapper {

    /**
     * 插入物流事件
     */
    int insert(LogisticsEvent event);

    /**
     * 根据ID查询
     */
    LogisticsEvent selectById(Long id);

    /**
     * 根据订单ID查询所有事件
     */
    List<LogisticsEvent> selectByOrderId(Long orderId);

    /**
     * 根据订单ID和状态查询
     */
    LogisticsEvent selectByOrderIdAndStatus(@Param("orderId") Long orderId, @Param("status") String status);

    /**
     * 查询订单的最新事件
     */
    LogisticsEvent selectLatestByOrderId(Long orderId);

    /**
     * 查询订单在异常状态之前的状态（即最后一个非异常状态）
     */
    LogisticsEvent selectLastNonExceptionStatusByOrderId(@Param("orderId") Long orderId);
}
