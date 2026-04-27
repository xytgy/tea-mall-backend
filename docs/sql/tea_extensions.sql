CREATE TABLE `tea_topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(100) NOT NULL COMMENT '话题标题，如 #春茶尝鲜#',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '话题描述',
  `participants_count` INT NOT NULL DEFAULT 0 COMMENT '参与人数统计',
  `posts_count` INT NOT NULL DEFAULT 0 COMMENT '相关动态数统计',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (1:正常, 0:停用)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='茶友圈话题表';

CREATE TABLE `tea_campaign` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(100) NOT NULL COMMENT '活动标题',
  `cover` VARCHAR(255) NOT NULL COMMENT '活动封面图片URL',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '活动简述',
  `link` VARCHAR(255) DEFAULT NULL COMMENT '活动跳转链接',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 (1:进行中, 0:已结束)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='茶友圈活动横幅表';

-- 插入一些初始数据
INSERT INTO `tea_topic` (`title`, `description`, `participants_count`, `posts_count`, `status`) VALUES
('#春茶尝鲜#', '分享你的第一口春茶体验', 12500, 4580, 1),
('#紫砂壶交流#', '晒出你心爱的紫砂壶', 8300, 2100, 1),
('#岩骨花香#', '武夷岩茶爱好者聚集地', 6200, 1850, 1);

INSERT INTO `tea_campaign` (`title`, `cover`, `description`, `link`, `status`) VALUES
('2026 春茶品鉴会报名中', 'https://images.unsplash.com/photo-1594631252845-29fc4cc8c0a1?ixlib=rb-4.0.3&auto=format&fit=crop&w=1000&q=80', '寻味山野，共品春光。名额有限，先到先得。', '/campaigns/1', 1);
