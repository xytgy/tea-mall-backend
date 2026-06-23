package com.xytgy.teamallbackend.mq.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xytgy.teamallbackend.mq.entity.MqRetryRecord;
import com.xytgy.teamallbackend.mq.mapper.MqRetryRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MQ 发送失败定时补偿任务
 * <p>
 * 每 10 秒扫描 PENDING 状态且到达下次重试时间的记录，进行重试。
 * 指数退避：10s, 30s, 90s, 270s（共 4 次）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RocketMQTemplate.class)
public class MqRetryScheduler {

    /**
     * 已经经历过第一次 10s 退避后，后续重试退避：30s, 90s, 270s
     */
    private static final int[] BACKOFF_SECONDS = {30, 90, 270};
    private static final int DEFAULT_MAX_RETRY = 4;
    private static final int CLAIM_BATCH_SIZE = 100;
    private static final int PROCESSING_TIMEOUT_MINUTES = 5;

    private final MqRetryRecordMapper retryRecordMapper;
    private final RocketMQTemplate rocketMQTemplate;

    @Scheduled(fixedDelay = 10_000, initialDelay = 10_000)
    public void retryPendingMessages() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusMinutes(PROCESSING_TIMEOUT_MINUTES);
        List<MqRetryRecord> records = retryRecordMapper.selectList(
                new LambdaQueryWrapper<MqRetryRecord>()
                        .and(wrapper -> wrapper
                                .eq(MqRetryRecord::getStatus, "PENDING")
                                .le(MqRetryRecord::getNextRetryTime, now)
                                .or(reclaim -> reclaim
                                        .eq(MqRetryRecord::getStatus, "PROCESSING")
                                        .le(MqRetryRecord::getUpdateTime, staleBefore)))
                        .orderByAsc(MqRetryRecord::getNextRetryTime)
                        .last("LIMIT " + CLAIM_BATCH_SIZE)
        );

        for (MqRetryRecord record : records) {
            if (!claimRecord(record, now, staleBefore)) {
                continue;
            }
            processRecord(record);
        }
    }

    private boolean claimRecord(MqRetryRecord record, LocalDateTime now, LocalDateTime staleBefore) {
        int updated = retryRecordMapper.update(null, new LambdaUpdateWrapper<MqRetryRecord>()
                .eq(MqRetryRecord::getId, record.getId())
                .and(wrapper -> wrapper
                        .eq(MqRetryRecord::getStatus, "PENDING")
                        .le(MqRetryRecord::getNextRetryTime, now)
                        .or(reclaim -> reclaim
                                .eq(MqRetryRecord::getStatus, "PROCESSING")
                                .le(MqRetryRecord::getUpdateTime, staleBefore)))
                .set(MqRetryRecord::getStatus, "PROCESSING")
                .set(MqRetryRecord::getLastError, null)
                .set(MqRetryRecord::getUpdateTime, LocalDateTime.now()));
        return updated == 1;
    }

    private void processRecord(MqRetryRecord record) {
        try {
            Message<String> msg = MessageBuilder.withPayload(record.getMessageBody())
                    .setHeader("KEYS", record.getMessageKey())
                    .build();

            if (record.getDelayLevel() != null && record.getDelayLevel() > 0) {
                rocketMQTemplate.syncSend(
                        record.getTopic() + ":" + record.getTags(), msg, 3000, record.getDelayLevel());
            } else {
                rocketMQTemplate.syncSend(
                        record.getTopic() + ":" + record.getTags(), msg);
            }

            // 发送成功 -> 标记 SUCCESS
            retryRecordMapper.update(null, new LambdaUpdateWrapper<MqRetryRecord>()
                    .eq(MqRetryRecord::getId, record.getId())
                    .set(MqRetryRecord::getStatus, "SUCCESS")
                    .set(MqRetryRecord::getLastError, null)
                    .set(MqRetryRecord::getUpdateTime, LocalDateTime.now()));
            log.info("MQ 重试成功: id={}, topic={}, tag={}, key={}",
                    record.getId(), record.getTopic(), record.getTags(), record.getMessageKey());

        } catch (Exception e) {
            int newCount = record.getRetryCount() + 1;
            int maxRetry = record.getMaxRetryCount() != null ? record.getMaxRetryCount() : DEFAULT_MAX_RETRY;
            if (newCount >= maxRetry) {
                // 超过最大重试次数 -> FAILED
                retryRecordMapper.update(null, new LambdaUpdateWrapper<MqRetryRecord>()
                        .eq(MqRetryRecord::getId, record.getId())
                        .set(MqRetryRecord::getStatus, "FAILED")
                        .set(MqRetryRecord::getRetryCount, newCount)
                        .set(MqRetryRecord::getLastError, truncateError(e))
                        .set(MqRetryRecord::getUpdateTime, LocalDateTime.now()));
                log.error("MQ 重试耗尽，标记为 FAILED: id={}, topic={}, tag={}, key={}, retryCount={}",
                        record.getId(), record.getTopic(), record.getTags(), record.getMessageKey(), newCount);
            } else {
                // 指数退避计算下次重试时间
                int index = Math.min(Math.max(newCount - 1, 0), BACKOFF_SECONDS.length - 1);
                int delaySeconds = BACKOFF_SECONDS[index];
                LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(delaySeconds);
                retryRecordMapper.update(null, new LambdaUpdateWrapper<MqRetryRecord>()
                        .eq(MqRetryRecord::getId, record.getId())
                        .set(MqRetryRecord::getStatus, "PENDING")
                        .set(MqRetryRecord::getRetryCount, newCount)
                        .set(MqRetryRecord::getNextRetryTime, nextRetry)
                        .set(MqRetryRecord::getLastError, truncateError(e))
                        .set(MqRetryRecord::getUpdateTime, LocalDateTime.now()));
                log.warn("MQ 重试失败，{}s 后再次重试: id={}, topic={}, tag={}, key={}, retryCount={}",
                        delaySeconds, record.getId(), record.getTopic(), record.getTags(),
                        record.getMessageKey(), newCount);
            }
        }
    }

    private String truncateError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
