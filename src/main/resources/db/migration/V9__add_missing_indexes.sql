-- =====================================================================
-- 缺失索引补充（幂等，使用 PREPARE 检查索引是否存在再创建）
-- =====================================================================

-- 1. 秒杀商品表：buy() 查询 flash_sale_id + product_id（每次秒杀请求必经）
SET @v9_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'flash_sale_product' AND index_name = 'idx_fsp_combo');
SET @v9_sql = IF(@v9_cnt = 0, 'CREATE INDEX idx_fsp_combo ON flash_sale_product (flash_sale_id, product_id)', 'SELECT 1');
PREPARE v9_stmt FROM @v9_sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

-- 2. 秒杀活动表：listActiveSales() 按 status + 时间范围过滤
SET @v9_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'flash_sale' AND index_name = 'idx_flash_sale_status_time');
SET @v9_sql = IF(@v9_cnt = 0, 'CREATE INDEX idx_flash_sale_status_time ON flash_sale (status, start_time, end_time)', 'SELECT 1');
PREPARE v9_stmt FROM @v9_sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

-- 3. 秒杀失败订单表：appeal() 按 user_id + flash_sale_id + status 查询
SET @v9_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'flash_sale_failed_order' AND index_name = 'idx_failed_order_user_sale');
SET @v9_sql = IF(@v9_cnt = 0, 'CREATE INDEX idx_failed_order_user_sale ON flash_sale_failed_order (user_id, flash_sale_id, status)', 'SELECT 1');
PREPARE v9_stmt FROM @v9_sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

-- 4. 通知表：优化 getUnreadCount() 覆盖索引 + listNotifications() ORDER BY 消除 filesort
--    替换原 idx_tea_notification_user_read (user_id, is_read) 为更完整的索引
SET @v9_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'tea_notification' AND index_name = 'idx_notification_user_read_time');
SET @v9_sql = IF(@v9_cnt = 0, 'CREATE INDEX idx_notification_user_read_time ON tea_notification (user_id, is_read, create_time DESC)', 'SELECT 1');
PREPARE v9_stmt FROM @v9_sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

-- 删除被新索引覆盖的旧索引
SET @v9_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'tea_notification' AND index_name = 'idx_tea_notification_user_read');
SET @v9_sql = IF(@v9_cnt > 0, 'DROP INDEX idx_tea_notification_user_read ON tea_notification', 'SELECT 1');
PREPARE v9_stmt FROM @v9_sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

-- 5. 支付记录表：getLastPayingRecord() 按 order_id + status 过滤
SET @v9_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'payment_record' AND index_name = 'idx_payment_record_order_status');
SET @v9_sql = IF(@v9_cnt = 0, 'CREATE INDEX idx_payment_record_order_status ON payment_record (order_id, status)', 'SELECT 1');
PREPARE v9_stmt FROM @v9_sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

-- 6. 客服工单表：listSupports() 按 user_id 过滤 + create_time 排序，消除 filesort
SET @v9_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'support_ticket' AND index_name = 'idx_support_ticket_user_time');
SET @v9_sql = IF(@v9_cnt = 0, 'CREATE INDEX idx_support_ticket_user_time ON support_ticket (user_id, create_time DESC)', 'SELECT 1');
PREPARE v9_stmt FROM @v9_sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

-- 删除被新索引覆盖的旧索引
SET @v9_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'support_ticket' AND index_name = 'idx_support_ticket_user_id');
SET @v9_sql = IF(@v9_cnt > 0, 'DROP INDEX idx_support_ticket_user_id ON support_ticket', 'SELECT 1');
PREPARE v9_stmt FROM @v9_sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

-- 7. 反馈表：listFeedback() 按 status 过滤 + create_time 排序，消除 filesort
SET @v9_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'feedback' AND index_name = 'idx_feedback_status_time');
SET @v9_sql = IF(@v9_cnt = 0, 'CREATE INDEX idx_feedback_status_time ON feedback (status, create_time DESC)', 'SELECT 1');
PREPARE v9_stmt FROM @v9_sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;

-- 删除被新索引覆盖的旧索引
SET @v9_cnt = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'feedback' AND index_name = 'idx_feedback_status');
SET @v9_sql = IF(@v9_cnt > 0, 'DROP INDEX idx_feedback_status ON feedback', 'SELECT 1');
PREPARE v9_stmt FROM @v9_sql; EXECUTE v9_stmt; DEALLOCATE PREPARE v9_stmt;
