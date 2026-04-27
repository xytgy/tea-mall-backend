CREATE TABLE IF NOT EXISTS `support_ticket` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '咨询记录ID',
  `user_id` BIGINT NOT NULL COMMENT '发起咨询的用户ID',
  `category` VARCHAR(50) NOT NULL COMMENT '咨询分类 (如: 订单问题, 产品咨询, 其他问题)',
  `title` VARCHAR(100) NOT NULL COMMENT '咨询标题',
  `content` VARCHAR(1000) NOT NULL COMMENT '咨询详细内容',
  `contact` VARCHAR(100) DEFAULT NULL COMMENT '用户联系方式(选填)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'unread' COMMENT '状态: unread(未回复), replied(已回复), resolved(已解决)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `reply_content` VARCHAR(1000) DEFAULT NULL COMMENT '客服回复内容',
  `reply_time` DATETIME DEFAULT NULL COMMENT '客服回复时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户咨询(工单)表';