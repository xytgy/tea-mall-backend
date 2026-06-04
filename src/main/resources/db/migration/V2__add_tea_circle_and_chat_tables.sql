CREATE TABLE IF NOT EXISTS `tea_campaign` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(100) NOT NULL COMMENT '活动标题',
  `cover` VARCHAR(255) NOT NULL COMMENT '活动封面图 URL',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '活动描述',
  `link` VARCHAR(255) DEFAULT NULL COMMENT '点击跳转的活动详情页 URL（选填）',
  `status` TINYINT NOT NULL DEFAULT '1' COMMENT '状态 (1:进行中, 0:已结束/停用)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='茶友圈活动横幅表';

CREATE TABLE IF NOT EXISTS `tea_topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(100) NOT NULL COMMENT '话题标题，例如：#春茶尝鲜',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '话题简介',
  `view_count` BIGINT NOT NULL DEFAULT '0',
  `post_count` BIGINT NOT NULL DEFAULT '0',
  `is_hot` TINYINT(1) NOT NULL DEFAULT '0' COMMENT '是否热门话题 (1:热门, 0:普通)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `name` VARCHAR(64) DEFAULT NULL COMMENT '话题唯一名，不带#，如：春茶品鉴',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tea_topic_title` (`title`),
  UNIQUE KEY `uk_tea_topic_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='茶友圈话题表';

CREATE TABLE IF NOT EXISTS `tea_post` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '动态ID',
  `user_id` BIGINT NOT NULL COMMENT '发布者ID',
  `content` TEXT COMMENT '动态内容',
  `images` TEXT COMMENT '图片JSON数组',
  `like_count` INT DEFAULT '0' COMMENT '点赞数',
  `comment_count` INT DEFAULT '0' COMMENT '评论数',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '是否删除 0否 1是',
  `delete_time` DATETIME DEFAULT NULL COMMENT '删除时间',
  PRIMARY KEY (`id`),
  KEY `idx_tea_post_user_id` (`user_id`),
  KEY `idx_tea_post_create_time` (`create_time`),
  KEY `idx_tea_post_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='茶友圈动态表';

CREATE TABLE IF NOT EXISTS `tea_post_topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `post_id` BIGINT NOT NULL,
  `topic_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tea_post_topic` (`post_id`, `topic_id`),
  KEY `idx_tea_post_topic_topic_id` (`topic_id`),
  KEY `idx_tea_post_topic_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动态-话题关联表';

CREATE TABLE IF NOT EXISTS `tea_comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `post_id` BIGINT NOT NULL COMMENT '动态ID',
  `user_id` BIGINT NOT NULL COMMENT '评论者ID',
  `content` VARCHAR(500) NOT NULL COMMENT '评论内容',
  `root_id` BIGINT DEFAULT NULL COMMENT '根评论ID(一级评论为null)',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID(回复某条评论的ID)',
  `reply_to_user_id` BIGINT DEFAULT NULL COMMENT '被回复者ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_tea_comment_post_id` (`post_id`),
  KEY `idx_tea_comment_root_id` (`root_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='茶友圈评论表';

CREATE TABLE IF NOT EXISTS `tea_like` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `post_id` BIGINT NOT NULL COMMENT '动态ID',
  `user_id` BIGINT NOT NULL COMMENT '点赞者ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tea_like_post_user` (`post_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='茶友圈点赞表';

CREATE TABLE IF NOT EXISTS `tea_follow` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `follower_id` BIGINT NOT NULL COMMENT '关注者ID',
  `following_id` BIGINT NOT NULL COMMENT '被关注者ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tea_follow_relation` (`follower_id`, `following_id`),
  KEY `idx_tea_follow_following_id` (`following_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='茶友圈关注表';

CREATE TABLE IF NOT EXISTS `tea_notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` BIGINT NOT NULL COMMENT '接收者ID',
  `type` VARCHAR(20) NOT NULL COMMENT '通知类型: like, comment, follow',
  `source_id` BIGINT NOT NULL COMMENT '来源ID(动态ID或用户ID等)',
  `actor_id` BIGINT NOT NULL COMMENT '触发者ID',
  `is_read` TINYINT(1) DEFAULT '0' COMMENT '是否已读 0未读 1已读',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tea_notification_user_id` (`user_id`),
  KEY `idx_tea_notification_is_read` (`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='茶友圈通知表';

CREATE TABLE IF NOT EXISTS `chat_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `buyer_id` BIGINT NOT NULL,
  `merchant_id` BIGINT NOT NULL,
  `last_message` VARCHAR(1000) DEFAULT NULL,
  `last_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_session_buyer_merchant` (`buyer_id`, `merchant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `session_id` BIGINT NOT NULL,
  `sender_id` BIGINT NOT NULL,
  `receiver_id` BIGINT NOT NULL,
  `content` VARCHAR(1000) NOT NULL,
  `msg_type` INT NOT NULL DEFAULT '0',
  `is_read` INT NOT NULL DEFAULT '0',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chat_message_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息记录表';
