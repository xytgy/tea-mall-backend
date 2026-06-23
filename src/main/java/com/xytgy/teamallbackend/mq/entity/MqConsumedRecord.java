package com.xytgy.teamallbackend.mq.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息消费幂等记录
 */
@Data
@TableName("mq_consumed_record")
public class MqConsumedRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消费组
     */
    private String consumerGroup;

    /**
     * 主题
     */
    private String topic;

    /**
     * 消费状态
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
