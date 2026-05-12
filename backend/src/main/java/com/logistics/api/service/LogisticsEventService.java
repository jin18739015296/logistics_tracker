package com.logistics.api.service;

public interface LogisticsEventService {
    
    void recordEvent(Long orderId, String status, String description, Long operatorId);
}
