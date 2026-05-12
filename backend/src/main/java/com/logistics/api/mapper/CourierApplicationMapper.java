package com.logistics.api.mapper;

import com.logistics.api.model.CourierApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourierApplicationMapper {

    CourierApplication selectById(Long id);

    CourierApplication selectByUserId(Long userId);
    
    /**
     * 根据手机号查询最新的申请记录
     */
    CourierApplication selectByPhone(@Param("phone") String phone);
    
    List<CourierApplication> selectByStatus(@Param("status") String status);
    
    List<CourierApplication> selectPending();
    
    /**
     * 查询所有申请（带用户信息）
     */
    List<CourierApplication> selectAllWithUser(@Param("status") String status, @Param("phone") String phone);
    
    int insert(CourierApplication application);
    
    int updateStatus(@Param("id") Long id, 
                     @Param("status") String status, 
                     @Param("reviewerId") Long reviewerId, 
                     @Param("rejectReason") String rejectReason);
    
    int deleteById(Long id);
}
