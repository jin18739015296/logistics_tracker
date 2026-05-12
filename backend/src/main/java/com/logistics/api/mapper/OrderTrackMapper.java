package com.logistics.api.mapper;

import com.logistics.api.model.OrderTrack;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderTrackMapper {

    List<OrderTrack> selectByOrderId(Long orderId);

    int insert(OrderTrack track);

    int insertBatch(List<OrderTrack> tracks);
}
