CREATE TABLE `feedback` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '反馈ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '提交人ID (如用户未登录或游客，可为空)',
  `type` VARCHAR(50) NOT NULL COMMENT '反馈类型 (功能建议, 内容错误, 界面美化, 其他问题)',
  `content` VARCHAR(1000) NOT NULL COMMENT '反馈内容详情',
  `images` JSON DEFAULT NULL COMMENT '上传的图片URL数组，最多3张',
  `contact` VARCHAR(100) DEFAULT NULL COMMENT '用户留下的联系方式(手机/邮箱等)',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态 (0:未处理, 1:已处理)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='意见反馈表';
