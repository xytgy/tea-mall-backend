-- =====================================================================
-- EXPLAIN 驱动的索引优化（幂等，使用 PREPARE 检查索引是否存在）
-- =====================================================================

-- 1. 订单列表：WHERE user_id=? ORDER BY create_time DESC
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'idx_orders_user_create_time');
SET @v6_sql = IF(@v6_cnt = 0, 'CREATE INDEX idx_orders_user_create_time ON `orders` (`user_id`, `create_time` DESC)', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- 2. 可售商品列表：WHERE status=1 AND audit_status=1 ORDER BY update_time DESC
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'product' AND index_name = 'idx_product_list');
SET @v6_sql = IF(@v6_cnt = 0, 'CREATE INDEX idx_product_list ON `product` (`status`, `audit_status`, `update_time` DESC)', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- 3. 商家商品：WHERE merchant_id=? ORDER BY update_time DESC
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'product' AND index_name = 'idx_product_merchant_update');
SET @v6_sql = IF(@v6_cnt = 0, 'CREATE INDEX idx_product_merchant_update ON `product` (`merchant_id`, `update_time` DESC)', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- 4. 待审核商品：WHERE audit_status=0 ORDER BY create_time ASC
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'product' AND index_name = 'idx_product_audit_create');
SET @v6_sql = IF(@v6_cnt = 0, 'CREATE INDEX idx_product_audit_create ON `product` (`audit_status`, `create_time`)', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- 5. 茶友圈广场：WHERE is_deleted=0 ORDER BY create_time DESC
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'tea_post' AND index_name = 'idx_tea_post_deleted_time');
SET @v6_sql = IF(@v6_cnt = 0, 'CREATE INDEX idx_tea_post_deleted_time ON `tea_post` (`is_deleted`, `create_time` DESC)', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- 6. 用户动态：WHERE user_id IN(...) AND is_deleted=0 ORDER BY create_time DESC
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'tea_post' AND index_name = 'idx_tea_post_user_time');
SET @v6_sql = IF(@v6_cnt = 0, 'CREATE INDEX idx_tea_post_user_time ON `tea_post` (`user_id`, `create_time` DESC)', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- 7. 话题帖子覆盖索引：WHERE topic_id=?
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'tea_post_topic' AND index_name = 'idx_post_topic_cover');
SET @v6_sql = IF(@v6_cnt = 0, 'CREATE INDEX idx_post_topic_cover ON `tea_post_topic` (`topic_id`, `post_id`)', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- 8. 聊天消息：WHERE session_id=? ORDER BY create_time DESC
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND index_name = 'idx_chat_message_session_time');
SET @v6_sql = IF(@v6_cnt = 0, 'CREATE INDEX idx_chat_message_session_time ON `chat_message` (`session_id`, `create_time` DESC)', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- 9. 聊天标记已读 + 未读统计
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND index_name = 'idx_chat_message_read_status');
SET @v6_sql = IF(@v6_cnt = 0, 'CREATE INDEX idx_chat_message_read_status ON `chat_message` (`session_id`, `receiver_id`, `is_read`)', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- 10. 商家会话列表：WHERE merchant_id=? ORDER BY update_time DESC
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'chat_session' AND index_name = 'idx_chat_session_merchant_time');
SET @v6_sql = IF(@v6_cnt = 0, 'CREATE INDEX idx_chat_session_merchant_time ON `chat_session` (`merchant_id`, `update_time` DESC)', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- =====================================================================
-- 冗余索引清理（幂等，使用 PREPARE 检查索引是否存在再删除）
-- =====================================================================

-- orders表：idx_orders_user_id 被 idx_orders_user_create_time 覆盖
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'idx_orders_user_id');
SET @v6_sql = IF(@v6_cnt > 0, 'DROP INDEX idx_orders_user_id ON `orders`', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- orders表：idx_orders_status 冗余
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'idx_orders_status');
SET @v6_sql = IF(@v6_cnt > 0, 'DROP INDEX idx_orders_status ON `orders`', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- tea_post表：idx_tea_post_user_id 被 idx_tea_post_user_time 覆盖
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'tea_post' AND index_name = 'idx_tea_post_user_id');
SET @v6_sql = IF(@v6_cnt > 0, 'DROP INDEX idx_tea_post_user_id ON `tea_post`', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- tea_post表：idx_tea_post_is_deleted 被 idx_tea_post_deleted_time 覆盖
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'tea_post' AND index_name = 'idx_tea_post_is_deleted');
SET @v6_sql = IF(@v6_cnt > 0, 'DROP INDEX idx_tea_post_is_deleted ON `tea_post`', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- product表：idx_product_status 被 idx_product_list 覆盖
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'product' AND index_name = 'idx_product_status');
SET @v6_sql = IF(@v6_cnt > 0, 'DROP INDEX idx_product_status ON `product`', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- product表：idx_product_audit_status 被 idx_product_audit_create 覆盖
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'product' AND index_name = 'idx_product_audit_status');
SET @v6_sql = IF(@v6_cnt > 0, 'DROP INDEX idx_product_audit_status ON `product`', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;

-- chat_message表：idx_chat_message_session_id 被 idx_chat_message_session_time 覆盖
SET @v6_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND index_name = 'idx_chat_message_session_id');
SET @v6_sql = IF(@v6_cnt > 0, 'DROP INDEX idx_chat_message_session_id ON `chat_message`', 'SELECT 1');
PREPARE v6_stmt FROM @v6_sql; EXECUTE v6_stmt; DEALLOCATE PREPARE v6_stmt;
