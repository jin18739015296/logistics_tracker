package com.logistics.api.service;

import com.logistics.api.model.MessageRecord;

/**
 * 消息记录服务接口
 */
public interface MessageRecordService {
    
    /**
     * 保存消息记录
     */
    void save(MessageRecord record);
    
    /**
     * 标记消息为已发送
     */
    void markAsSent(Long messageId);
    
    /**
     * 标记消息为发送失败
     */
    void markAsFailed(Long messageId, String errorMsg);
}
