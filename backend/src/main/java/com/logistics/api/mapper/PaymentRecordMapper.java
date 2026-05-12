package com.logistics.api.mapper;

import com.logistics.api.model.PaymentRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaymentRecordMapper {

    int insert(PaymentRecord record);

    PaymentRecord selectById(Long id);

    PaymentRecord selectByOrderId(Long orderId);

    PaymentRecord selectByPaymentNo(String paymentNo);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("transactionId") String transactionId);

    @Select("SELECT p.* FROM payment_records p " +
            "JOIN orders o ON p.order_id = o.id " +
            "WHERE o.user_id = #{userId} " +
            "ORDER BY p.create_time DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<PaymentRecord> selectByUserId(@Param("userId") Long userId,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    @Select("SELECT p.* FROM payment_records p " +
            "JOIN orders o ON p.order_id = o.id " +
            "WHERE o.user_id = #{userId} AND p.status = #{status} " +
            "ORDER BY p.create_time DESC " +
            "LIMIT #{limit} OFFSET #{offset}")
    List<PaymentRecord> selectByUserIdAndStatus(@Param("userId") Long userId,
                                                 @Param("status") String status,
                                                 @Param("limit") int limit,
                                                 @Param("offset") int offset);
}
