package com.logistics.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 订单自动进入异常（exception）的阈值，可通过配置关闭或调大时长避免演示环境误触发。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.order.auto-exception")
public class OrderAutoExceptionProperties {

    /** 是否启用定时扫描并自动标记异常 */
    private boolean enabled = true;

    /** 待配送员确认接单超过该分钟数 → system_awaiting_courier_confirm_timeout */
    private long awaitingCourierConfirmMinutes = 60;
}
