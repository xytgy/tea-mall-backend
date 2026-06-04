-- 统一添加软删除字段（幂等，使用 PREPARE 检查列是否存在）

-- 商品表
SET @v5_cnt = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'product' AND column_name = 'is_deleted');
SET @v5_sql = IF(@v5_cnt = 0, "ALTER TABLE `product` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `sales`", 'SELECT 1');
PREPARE v5_stmt FROM @v5_sql; EXECUTE v5_stmt; DEALLOCATE PREPARE v5_stmt;

-- 店铺表
SET @v5_cnt = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'shop' AND column_name = 'is_deleted');
SET @v5_sql = IF(@v5_cnt = 0, "ALTER TABLE `shop` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `update_time`", 'SELECT 1');
PREPARE v5_stmt FROM @v5_sql; EXECUTE v5_stmt; DEALLOCATE PREPARE v5_stmt;

-- 订单表
SET @v5_cnt = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'is_deleted');
SET @v5_sql = IF(@v5_cnt = 0, "ALTER TABLE `orders` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `settle_time`", 'SELECT 1');
PREPARE v5_stmt FROM @v5_sql; EXECUTE v5_stmt; DEALLOCATE PREPARE v5_stmt;

-- 购物车表
SET @v5_cnt = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'cart' AND column_name = 'is_deleted');
SET @v5_sql = IF(@v5_cnt = 0, "ALTER TABLE `cart` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `update_time`", 'SELECT 1');
PREPARE v5_stmt FROM @v5_sql; EXECUTE v5_stmt; DEALLOCATE PREPARE v5_stmt;

-- 收藏表
SET @v5_cnt = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'favorite' AND column_name = 'is_deleted');
SET @v5_sql = IF(@v5_cnt = 0, "ALTER TABLE `favorite` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `update_time`", 'SELECT 1');
PREPARE v5_stmt FROM @v5_sql; EXECUTE v5_stmt; DEALLOCATE PREPARE v5_stmt;

-- 用户地址表
SET @v5_cnt = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'user_address' AND column_name = 'is_deleted');
SET @v5_sql = IF(@v5_cnt = 0, "ALTER TABLE `user_address` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `update_time`", 'SELECT 1');
PREPARE v5_stmt FROM @v5_sql; EXECUTE v5_stmt; DEALLOCATE PREPARE v5_stmt;

-- 茶友圈话题表
SET @v5_cnt = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tea_topic' AND column_name = 'is_deleted');
SET @v5_sql = IF(@v5_cnt = 0, "ALTER TABLE `tea_topic` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `update_time`", 'SELECT 1');
PREPARE v5_stmt FROM @v5_sql; EXECUTE v5_stmt; DEALLOCATE PREPARE v5_stmt;

-- 茶友圈活动表
SET @v5_cnt = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'tea_campaign' AND column_name = 'is_deleted');
SET @v5_sql = IF(@v5_cnt = 0, "ALTER TABLE `tea_campaign` ADD COLUMN `is_deleted` TINYINT(1) DEFAULT '0' COMMENT '逻辑删除 0否 1是' AFTER `create_time`", 'SELECT 1');
PREPARE v5_stmt FROM @v5_sql; EXECUTE v5_stmt; DEALLOCATE PREPARE v5_stmt;
