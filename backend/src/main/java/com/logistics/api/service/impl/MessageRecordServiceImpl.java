package com.logistics.api.service.impl;

import com.logistics.api.mapper.MessageRecordMapper;
import com.logistics.api.model.MessageRecord;
import com.logistics.api.service.MessageRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 消息记录服务实现
 */
@Slf4j
@Service
public class MessageRecordServiceImpl implements MessageRecordService {

    @Autowired
    private MessageRecordMapper messageRecordMapper;

    @Override
    public void save(MessageRecord record) {
        messageRecordMapper.insert(record);
    }

    @Override
    public void markAsSent(Long messageId) {
        messageRecordMapper.updateStatus(messageId, MessageRecord.STATUS_SENT, null);
    }

    @Override
    public void markAsFailed(Long messageId, String errorMsg) {
        messageRecordMapper.updateStatus(messageId, MessageRecord.STATUS_FAILED, errorMsg);
        messageRecordMapper.incrementRetryCount(messageId);
    }
}
