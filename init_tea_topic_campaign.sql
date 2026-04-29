-- 1. 话题表 (Tea Topic)
CREATE TABLE IF NOT EXISTS `tea_topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(64) DEFAULT NULL COMMENT '话题唯一名，不带#，如：春茶品鉴',
  `title` VARCHAR(100) NOT NULL COMMENT '话题标题，例如：#春茶尝鲜',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '话题简介',
  `view_count` BIGINT NOT NULL DEFAULT 0 COMMENT '围观人数(真实数字)',
  `post_count` BIGINT NOT NULL DEFAULT 0 COMMENT '该话题下的讨论动态数',
  `is_hot` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否热门话题 (1:热门, 0:普通)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_title` (`title`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='茶友圈话题表';

-- 2. 活动横幅表 (Tea Campaign)
CREATE TABLE IF NOT EXISTS `tea_campaign` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(100) NOT NULL COMMENT '活动标题',
  `cover` VARCHAR(255) NOT NULL COMMENT '活动封面图 URL',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '活动描述',
  `link` VARCHAR(255) DEFAULT NULL COMMENT '点击跳转的活动详情页 URL（选填）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (1:进行中, 0:已结束/停用)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='茶友圈活动横幅表';

-- 插入一些初始化测试数据
INSERT IGNORE INTO `tea_topic` (`title`, `description`, `view_count`, `post_count`, `is_hot`) VALUES
('#春茶品鉴', '春日游，杏花吹满头。在这个万物复苏的季节，一起来分享你的第一口春茶吧。', 125000, 342, 1),
('#紫砂壶', '泥绘春秋，壶中日月。紫砂壶的养护、鉴赏与交流。', 82000, 156, 1),
('#茶山游', '寻味山野，探访名山名枞。分享你的茶山行记与见闻。', 45000, 89, 0),
('#明前龙井', '明前茶，贵如金。西湖龙井的核心产区探秘与品鉴心得。', 158000, 512, 1),
('#武夷岩茶', '岩骨花香，半壁江山。肉桂、水仙、大红袍的冲泡与品鉴。', 67000, 120, 0);

INSERT IGNORE INTO `tea_campaign` (`title`, `cover`, `description`, `status`) VALUES
('2026 春茶品鉴会报名中', 'https://coresg-normal.trae.ai/api/ide/v1/text_to_image?prompt=An%20elegant%20spring%20tea%20tasting%20event%20banner%2C%20serene%20tea%20ceremony%2C%20blooming%20spring%20flowers%2C%20beautiful%20tea%20sets%2C%20bright%20and%20inviting%2C%20aesthetic%20composition&image_size=landscape_16_9', '寻味山野，共品春光。名额有限，先到先得。', 1);
