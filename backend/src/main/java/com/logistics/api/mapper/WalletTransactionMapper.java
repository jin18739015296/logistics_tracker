package com.logistics.api.mapper;

import com.logistics.api.model.WalletTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WalletTransactionMapper {

    WalletTransaction selectById(Long id);

    List<WalletTransaction> selectByUserId(@Param("userId") Long userId);

    List<WalletTransaction> selectByUserIdAndType(@Param("userId") Long userId, @Param("type") String type);

    int insert(WalletTransaction transaction);

    @Select("SELECT * FROM wallet_transaction WHERE user_id = #{userId} " +
            "ORDER BY create_time DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<WalletTransaction> selectByUserIdWithPagination(@Param("userId") Long userId,
                                                          @Param("limit") int limit,
                                                          @Param("offset") int offset);

    @Select("SELECT * FROM wallet_transaction WHERE user_id = #{userId} AND type = #{type} " +
            "ORDER BY create_time DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<WalletTransaction> selectByUserIdAndTypeWithPagination(@Param("userId") Long userId,
                                                                 @Param("type") String type,
                                                                 @Param("limit") int limit,
                                                                 @Param("offset") int offset);

    /**
     * 查询某订单已退款总额（通过流水表汇总）
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction WHERE order_id = #{orderId} AND type = 'refund'")
    BigDecimal sumRefundAmountByOrderId(@Param("orderId") Long orderId);

    /**
     * 按用户ID和时间范围统计收入总额（type = 'income'）
     */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM wallet_transaction WHERE user_id = #{userId} AND type = 'income' AND create_time >= #{startTime} AND create_time <= #{endTime}")
    BigDecimal sumIncomeByUserIdAndTimeRange(@Param("userId") Long userId,
                                               @Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);
}
