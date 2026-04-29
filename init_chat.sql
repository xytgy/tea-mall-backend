CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
  `sender` VARCHAR(20) NOT NULL COMMENT '发送方类型: user 或 merchant',
  `content` VARCHAR(1000) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_merchant` (`user_id`, `merchant_id`),
  KEY `idx_merchant_user` (`merchant_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息记录表';