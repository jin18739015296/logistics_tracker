package com.logistics.api.mapper;

import com.logistics.api.model.Complaint;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ComplaintMapper {

    @Insert("INSERT INTO complaint (complaint_no, order_id, user_id, courier_id, type, title, content, images, status) " +
            "VALUES (#{complaintNo}, #{orderId}, #{userId}, #{courierId}, #{type}, #{title}, #{content}, #{images}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Complaint complaint);

    @Select("SELECT * FROM complaint WHERE id = #{id}")
    Complaint selectById(Long id);

    @Select("SELECT * FROM complaint WHERE order_id = #{orderId}")
    List<Complaint> selectByOrderId(Long orderId);

    @Select("SELECT * FROM complaint WHERE order_id = #{orderId} AND user_id = #{userId} LIMIT 1")
    Complaint selectByOrderIdAndUserId(@Param("orderId") Long orderId, @Param("userId") Long userId);

    @Select("SELECT * FROM complaint WHERE user_id = #{userId} AND status != 'cancelled' "
            + "ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Complaint> selectByUserIdPaged(@Param("userId") Long userId,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    @Select("SELECT * FROM complaint WHERE courier_id = #{courierId} AND status != 'cancelled' " +
            "ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Complaint> selectByCourierIdPaged(@Param("courierId") Long courierId,
                                           @Param("limit") int limit,
                                           @Param("offset") int offset);

    @Select("SELECT * FROM complaint WHERE status = #{status} AND status != 'cancelled' " +
            "ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Complaint> selectByStatus(@Param("status") String status,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    @Select("SELECT * FROM complaint WHERE status != 'cancelled' ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<Complaint> selectAll(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM complaint WHERE status = #{status}")
    int countByStatus(String status);

    @Update("UPDATE complaint SET status = #{status}, result = #{result}, result_content = #{resultContent}, " +
            "handler_id = #{handlerId}, handler_name = #{handlerName}, handle_time = NOW() " +
            "WHERE id = #{id}")
    int updateHandleResult(Complaint complaint);

    @Update("UPDATE complaint SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateById(Complaint complaint);

}
