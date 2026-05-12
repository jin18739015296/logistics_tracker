package com.logistics.api.mapper;

import com.logistics.api.model.CourierReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourierReviewMapper {

    /**
     * 插入评价
     */
    int insert(CourierReview review);

    /**
     * 根据ID查询
     */
    CourierReview selectById(Long id);

    /**
     * 根据配送员ID查询评价列表
     */
    List<CourierReview> selectByCourierId(@Param("courierId") Long courierId, 
                                          @Param("offset") int offset, 
                                          @Param("limit") int limit);

    /**
     * 根据订单ID查询评价
     */
    CourierReview selectByOrderId(Long orderId);

    /**
     * 根据订单ID和用户ID查询评价
     */
    CourierReview selectByOrderIdAndUserId(@Param("orderId") Long orderId, @Param("userId") Long userId);

    /**
     * 获取配送员平均评分
     */
    Double selectAverageRatingByCourierId(Long courierId);

    /**
     * 获取配送员评价数量
     */
    int selectCountByCourierId(Long courierId);

    /**
     * 获取评分分布
     */
    List<java.util.Map<String, Object>> selectRatingDistribution(Long courierId);
}
