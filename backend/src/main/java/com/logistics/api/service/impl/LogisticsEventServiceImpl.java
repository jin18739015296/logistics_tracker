package com.logistics.api.service.impl;

import com.logistics.api.mapper.LogisticsEventMapper;
import com.logistics.api.model.LogisticsEvent;
import com.logistics.api.service.LogisticsEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LogisticsEventServiceImpl implements LogisticsEventService {

    @Autowired
    private LogisticsEventMapper logisticsEventMapper;

    @Override
    public void recordEvent(Long orderId, String status, String description, Long operatorId) {
        LogisticsEvent event = new LogisticsEvent();
        event.setOrderId(orderId);
        event.setStatus(status);
        event.setDescription(description);
        event.setOperatorId(operatorId);
        event.setCreateTime(LocalDateTime.now());
        logisticsEventMapper.insert(event);
    }
}
