-- 统一添加软删除字段

-- 商品表
ALTER TABLE `product` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `sales`;

-- 店铺表
ALTER TABLE `shop` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `update_time`;

-- 订单表
ALTER TABLE `orders` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `settle_time`;

-- 购物车表
ALTER TABLE `cart` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `update_time`;

-- 收藏表
ALTER TABLE `favorite` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `update_time`;

-- 用户地址表
ALTER TABLE `user_address` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `update_time`;

-- 茶友圈话题表
ALTER TABLE `tea_topic` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `update_time`;

-- 茶友圈活动表
ALTER TABLE `tea_campaign` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `create_time`;
