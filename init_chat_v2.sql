DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `chat_session`;

CREATE TABLE `chat_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `buyer_id` BIGINT NOT NULL,
  `merchant_id` BIGINT NOT NULL,
  `last_message` VARCHAR(1000),
  `last_time` DATETIME,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_buyer_merchant` (`buyer_id`, `merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

CREATE TABLE `chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `session_id` BIGINT NOT NULL,
  `sender_id` BIGINT NOT NULL,
  `receiver_id` BIGINT NOT NULL,
  `content` VARCHAR(1000) NOT NULL,
  `msg_type` INT NOT NULL DEFAULT 0,
  `is_read` INT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息记录表';