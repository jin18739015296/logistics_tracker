package com.logistics.api.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logistics.api.mapper.MessageRecordMapper;
import com.logistics.api.messaging.MessageSender;
import com.logistics.api.model.MessageRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息补偿定时任务
 * 定时扫描本地消息表，补偿发送失败或待发送的消息
 */
@Slf4j
@Component
public class MessageCompensateJob {

    @Autowired
    private MessageRecordMapper messageRecordMapper;

    @Autowired
    private MessageSender messageSender;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 5;

    /**
     * 每次处理的消息数量
     */
    private static final int BATCH_SIZE = 100;

    /**
     * 补偿待发送的消息（每30秒执行一次）
     * 处理状态为 PENDING(0) 的消息
     */
    @Scheduled(fixedRate = 30000)
    public void compensatePendingMessages() {
        log.debug("开始补偿待发送消息...");

        try {
            List<MessageRecord> pendingMessages = messageRecordMapper.selectPendingMessages(BATCH_SIZE);

            if (pendingMessages.isEmpty()) {
                log.debug("没有待发送的消息需要补偿");
                return;
            }

            log.info("发现 {} 条待发送消息需要补偿", pendingMessages.size());

            for (MessageRecord message : pendingMessages) {
                processMessage(message);
            }

        } catch (Exception e) {
            log.error("补偿待发送消息时发生错误", e);
        }
    }

    /**
     * 重试发送失败的消息（每60秒执行一次）
     * 处理状态为 FAILED(2) 且重试次数未达上限的消息
     */
    @Scheduled(fixedRate = 60000)
    public void retryFailedMessages() {
        log.debug("开始重试发送失败的消息...");

        try {
            List<MessageRecord> failedMessages = messageRecordMapper.selectFailedMessages(MAX_RETRY_COUNT, BATCH_SIZE);

            if (failedMessages.isEmpty()) {
                log.debug("没有失败的消息需要重试");
                return;
            }

            log.info("发现 {} 条失败消息需要重试", failedMessages.size());

            for (MessageRecord message : failedMessages) {
                // 增加重试次数
                messageRecordMapper.incrementRetryCount(message.getId());
                processMessage(message);
            }

        } catch (Exception e) {
            log.error("重试失败消息时发生错误", e);
        }
    }

    /**
     * 处理单条消息
     */
    private void processMessage(MessageRecord message) {
        log.info("处理消息, messageId: {}, messageType: {}, businessId: {}, retryCount: {}",
                message.getId(), message.getMessageType(), message.getBusinessId(), message.getRetryCount());

        try {
            // 解析消息内容
            Object payload = objectMapper.readValue(message.getPayload(), Object.class);

            // 使用 MessageSender 组件发送消息（带本地消息表保障）
            messageSender.send(
                    message.getMessageType(),
                    message.getBusinessId(),
                    payload,
                    message.getExchange(),
                    message.getRoutingKey(),
                    message.getQueueName()
            );

            // 更新状态为发送成功
            messageRecordMapper.updateStatus(message.getId(), MessageRecord.STATUS_SENT, null);
            log.info("消息补偿成功, messageId: {}", message.getId());

        } catch (Exception e) {
            log.error("消息补偿失败, messageId: {}, error: {}", message.getId(), e.getMessage());

            // 更新状态为失败，记录错误信息
            int newStatus = message.getRetryCount() >= MAX_RETRY_COUNT - 1
                    ? MessageRecord.STATUS_FAILED
                    : MessageRecord.STATUS_FAILED;
            messageRecordMapper.updateStatus(message.getId(), newStatus, e.getMessage());
        }
    }
}
