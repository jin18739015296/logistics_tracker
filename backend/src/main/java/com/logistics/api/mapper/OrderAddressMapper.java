package com.logistics.api.mapper;

import com.logistics.api.model.OrderAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrderAddressMapper {

    OrderAddress selectById(Long id);

    List<OrderAddress> selectByOrderId(Long orderId);

    OrderAddress selectByOrderIdAndType(@Param("orderId") Long orderId, @Param("type") String type);

    List<OrderAddress> selectByOrderIds(@Param("orderIds") List<Long> orderIds);

    int insert(OrderAddress orderAddress);

    int insertBatch(List<OrderAddress> orderAddresses);

    int updateById(OrderAddress orderAddress);

    int deleteByOrderId(Long orderId);
}
