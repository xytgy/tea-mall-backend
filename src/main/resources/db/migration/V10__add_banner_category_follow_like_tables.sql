-- Banner 轮播图表
CREATE TABLE IF NOT EXISTS `banner` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(100) NOT NULL COMMENT '轮播图标题',
  `image_url` VARCHAR(500) NOT NULL COMMENT '轮播图图片URL',
  `link_url` VARCHAR(500) DEFAULT NULL COMMENT '点击跳转链接',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序，越大越靠前',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_banner_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页轮播图表';

-- 商品分类表
CREATE TABLE IF NOT EXISTS `product_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
  `icon` VARCHAR(500) DEFAULT NULL COMMENT '分类图标URL',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序，越大越靠前',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0禁用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- 店铺关注表
CREATE TABLE IF NOT EXISTS `shop_follow` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '关注者用户ID',
  `shop_id` BIGINT NOT NULL COMMENT '被关注的店铺ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_follow` (`user_id`, `shop_id`),
  KEY `idx_shop_follow_shop_id` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺关注表';

-- 评论点赞表
CREATE TABLE IF NOT EXISTS `comment_like` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `comment_id` BIGINT NOT NULL COMMENT '评论ID',
  `user_id` BIGINT NOT NULL COMMENT '点赞者ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_like` (`comment_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论点赞表';

-- 种子数据：轮播图
INSERT INTO `banner` (`title`, `image_url`, `link_url`, `sort_order`, `status`) VALUES
('春茶上新', 'https://via.placeholder.com/750x300/27AE60/FFFFFF?text=春茶上新', NULL, 3, 1),
('限时特惠', 'https://via.placeholder.com/750x300/E74C3C/FFFFFF?text=限时特惠', NULL, 2, 1),
('新人专享', 'https://via.placeholder.com/750x300/3498DB/FFFFFF?text=新人专享', NULL, 1, 1);

-- 种子数据：商品分类
INSERT INTO `product_category` (`name`, `icon`, `sort_order`, `status`) VALUES
('绿茶', 'https://via.placeholder.com/80/27AE60/FFFFFF?text=绿', 8, 1),
('红茶', 'https://via.placeholder.com/80/E74C3C/FFFFFF?text=红', 7, 1),
('乌龙茶', 'https://via.placeholder.com/80/9B59B6/FFFFFF?text=乌', 6, 1),
('白茶', 'https://via.placeholder.com/80/BDC3C7/333333?text=白', 5, 1),
('黑茶', 'https://via.placeholder.com/80/2C3E50/FFFFFF?text=黑', 4, 1),
('花茶', 'https://via.placeholder.com/80/F39C12/FFFFFF?text=花', 3, 1),
('养生茶', 'https://via.placeholder.com/80/1ABC9C/FFFFFF?text=养', 2, 1),
('茶具', 'https://via.placeholder.com/80/95A5A6/FFFFFF?text=具', 1, 1);
