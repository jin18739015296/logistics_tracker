package com.logistics.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置
 * 启用 Spring 的定时任务功能
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // 配置类，启用 @Scheduled 注解
}
