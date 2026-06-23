package com.xytgy.teamallbackend.mq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MQ 发送失败本地重试记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("mq_retry_record")
public class MqRetryRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 消息 topic
     */
    private String topic;

    /**
     * 消息 tag
     */
    private String tags;

    /**
     * 消息 key
     */
    private String messageKey;

    /**
     * 消息体 JSON
     */
    private String messageBody;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;

    /**
     * 状态：PENDING / PROCESSING / SUCCESS / FAILED
     */
    private String status;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;

    /**
     * 延迟级别（延迟消息专用，普通消息为 null）
     */
    private Integer delayLevel;

    /**
     * 最近一次发送或重试失败原因
     */
    private String lastError;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
