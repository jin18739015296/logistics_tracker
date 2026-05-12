package com.logistics.api.mapper;

import com.logistics.api.model.MessageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageRecordMapper {

    /**
     * 插入消息记录
     */
    int insert(MessageRecord record);

    /**
     * 根据ID查询
     */
    MessageRecord selectById(Long id);

    /**
     * 根据业务ID和消息类型查询
     */
    MessageRecord selectByBusinessIdAndType(@Param("businessId") Long businessId, 
                                            @Param("messageType") String messageType);

    /**
     * 查询待发送的消息（用于定时任务补偿）
     */
    List<MessageRecord> selectPendingMessages(@Param("limit") int limit);

    /**
     * 查询发送失败的消息（用于定时任务重试）
     */
    List<MessageRecord> selectFailedMessages(@Param("maxRetry") int maxRetry, 
                                             @Param("limit") int limit);

    /**
     * 更新消息状态
     */
    int updateStatus(@Param("id") Long id, 
                     @Param("status") Integer status, 
                     @Param("errorMsg") String errorMsg);

    /**
     * 增加重试次数
     */
    int incrementRetryCount(@Param("id") Long id);

    /**
     * 更新消息为消费成功
     */
    int markAsConsumed(@Param("businessId") Long businessId, 
                       @Param("messageType") String messageType);
}
