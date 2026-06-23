package com.xytgy.teamallbackend.mq.util;

import com.xytgy.teamallbackend.mq.entity.MqConsumedRecord;
import com.xytgy.teamallbackend.mq.mapper.MqConsumedRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 消息幂等消费工具类。
 * <p>
 * 利用数据库唯一索引 (message_id, consumer_group) 保证同一条消息
 * 在同一消费组内不会被重复处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentUtil {

    private final MqConsumedRecordMapper consumedRecordMapper;

    /**
     * 尝试标记消息为已消费。
     * <p>
     * 通过 INSERT 唯一键冲突判断是否重复消费：
     * <ul>
     *   <li>INSERT 成功 → 返回 true，表示首次消费</li>
     *   <li>抛出 DuplicateKeyException → 返回 false，表示已消费过</li>
     * </ul>
     *
     * @param messageId     消息唯一标识
     * @param consumerGroup 消费组名称
     * @param topic         消息主题
     * @return true 如果是首次消费，false 如果已消费过
     */
    public boolean tryConsume(String messageId, String consumerGroup, String topic) {
        try {
            MqConsumedRecord record = new MqConsumedRecord();
            // id 由 MyBatis-Plus IdType.ASSIGN_ID 自动生成（雪花算法）
            record.setMessageId(messageId);
            record.setConsumerGroup(consumerGroup);
            record.setTopic(topic);
            record.setStatus("SUCCESS");
            record.setCreateTime(LocalDateTime.now());
            consumedRecordMapper.insert(record);
            return true;
        } catch (DuplicateKeyException e) {
            // 唯一键冲突，说明已消费过
            log.debug("消息已消费过, messageId={}, consumerGroup={}", messageId, consumerGroup);
            return false;
        }
    }
}
