-- 性能优化：添加缺失索引（幂等，使用 PREPARE 检查索引是否存在）

-- product表：优化 listAvailableProducts 查询
SET @v4_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'product' AND index_name = 'idx_product_available');
SET @v4_sql = IF(@v4_cnt = 0, 'CREATE INDEX idx_product_available ON `product` (`status`, `audit_status`, `stock`)', 'SELECT 1');
PREPARE v4_stmt FROM @v4_sql; EXECUTE v4_stmt; DEALLOCATE PREPARE v4_stmt;

-- product_review表：优化批量查询用户信息
SET @v4_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'product_review' AND index_name = 'idx_product_review_user_id');
SET @v4_sql = IF(@v4_cnt = 0, 'CREATE INDEX idx_product_review_user_id ON `product_review` (`user_id`)', 'SELECT 1');
PREPARE v4_stmt FROM @v4_sql; EXECUTE v4_stmt; DEALLOCATE PREPARE v4_stmt;

-- cart表：优化购物车查询和删除
SET @v4_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'cart' AND index_name = 'idx_cart_user_product');
SET @v4_sql = IF(@v4_cnt = 0, 'CREATE INDEX idx_cart_user_product ON `cart` (`user_id`, `product_id`)', 'SELECT 1');
PREPARE v4_stmt FROM @v4_sql; EXECUTE v4_stmt; DEALLOCATE PREPARE v4_stmt;

-- orders表：优化用户订单列表查询
SET @v4_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'idx_orders_user_status');
SET @v4_sql = IF(@v4_cnt = 0, 'CREATE INDEX idx_orders_user_status ON `orders` (`user_id`, `status`)', 'SELECT 1');
PREPARE v4_stmt FROM @v4_sql; EXECUTE v4_stmt; DEALLOCATE PREPARE v4_stmt;

-- orders表：优化按时间排序的查询
SET @v4_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'idx_orders_create_time');
SET @v4_sql = IF(@v4_cnt = 0, 'CREATE INDEX idx_orders_create_time ON `orders` (`create_time`)', 'SELECT 1');
PREPARE v4_stmt FROM @v4_sql; EXECUTE v4_stmt; DEALLOCATE PREPARE v4_stmt;

-- tea_comment表：优化批量查询用户信息
SET @v4_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'tea_comment' AND index_name = 'idx_tea_comment_user_id');
SET @v4_sql = IF(@v4_cnt = 0, 'CREATE INDEX idx_tea_comment_user_id ON `tea_comment` (`user_id`)', 'SELECT 1');
PREPARE v4_stmt FROM @v4_sql; EXECUTE v4_stmt; DEALLOCATE PREPARE v4_stmt;

-- tea_notification表：优化查询未读通知
SET @v4_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'tea_notification' AND index_name = 'idx_tea_notification_user_read');
SET @v4_sql = IF(@v4_cnt = 0, 'CREATE INDEX idx_tea_notification_user_read ON `tea_notification` (`user_id`, `is_read`)', 'SELECT 1');
PREPARE v4_stmt FROM @v4_sql; EXECUTE v4_stmt; DEALLOCATE PREPARE v4_stmt;

-- chat_message表：优化查询未读消息
SET @v4_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND index_name = 'idx_chat_message_receiver_read');
SET @v4_sql = IF(@v4_cnt = 0, 'CREATE INDEX idx_chat_message_receiver_read ON `chat_message` (`receiver_id`, `is_read`)', 'SELECT 1');
PREPARE v4_stmt FROM @v4_sql; EXECUTE v4_stmt; DEALLOCATE PREPARE v4_stmt;
