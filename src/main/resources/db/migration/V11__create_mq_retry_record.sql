-- MQ 发送失败本地重试表
CREATE TABLE IF NOT EXISTS `mq_retry_record` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `topic`           VARCHAR(128) NOT NULL COMMENT '消息 topic',
    `tags`            VARCHAR(128) DEFAULT NULL COMMENT '消息 tag',
    `message_key`     VARCHAR(256) DEFAULT NULL COMMENT '消息 key',
    `message_body`    TEXT         NOT NULL COMMENT '消息体 JSON',
    `retry_count`     INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `max_retry_count` INT          NOT NULL DEFAULT 4 COMMENT '最大重试次数',
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SUCCESS/FAILED',
    `next_retry_time` DATETIME     NOT NULL COMMENT '下次重试时间',
    `delay_level`     INT          DEFAULT NULL COMMENT '延迟级别（延迟消息专用）',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_status_next_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ 发送失败本地重试记录';
