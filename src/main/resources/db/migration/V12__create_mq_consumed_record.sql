CREATE TABLE IF NOT EXISTS `mq_consumed_record` (
    `id` BIGINT NOT NULL,
    `message_id` VARCHAR(128) NOT NULL COMMENT '消息ID',
    `consumer_group` VARCHAR(64) NOT NULL COMMENT '消费组',
    `topic` VARCHAR(64) NOT NULL COMMENT '主题',
    `status` VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '消费状态',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_consumer` (`message_id`, `consumer_group`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息消费记录表';
