package com.logistics.api.job;

import com.logistics.api.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 按配置扫描订单并自动标记为异常（exception），与人工/配送员上报互补。
 */
@Slf4j
@Component
public class OrderAutoExceptionJob {

    @Autowired
    private OrderService orderService;

    @Scheduled(fixedDelay = 300000)
    public void scanAndMarkExceptions() {
        try {
            orderService.runAutoExceptionRules();
        } catch (Exception e) {
            log.error("自动异常规则执行失败", e);
        }
    }
}
