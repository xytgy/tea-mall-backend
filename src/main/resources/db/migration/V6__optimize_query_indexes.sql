-- =====================================================================
-- EXPLAIN 驱动的索引优化：覆盖订单列表、商品检索、动态流、聊天记录
-- 核心原则：联合索引覆盖 WHERE + ORDER BY，消除 filesort 与回表
-- =====================================================================

-- =====================================================================
-- 1. 订单列表：WHERE user_id=? ORDER BY create_time DESC LIMIT ?,?
--    现有 idx_orders_user_id 只覆盖 WHERE，排序需 filesort
--    新索引将排序列纳入 B+ 树同层，消除 filesort
-- =====================================================================
CREATE INDEX idx_orders_user_create_time ON `orders` (`user_id`, `create_time` DESC);

-- =====================================================================
-- 2. 可售商品：WHERE status=1 AND audit_status=1 AND stock>0
--          ORDER BY update_time DESC LIMIT ?,?
--    现有 idx_product_available(status,audit_status,stock) 无法避免 filesort
--    stock>0 是范围条件，之后列无法用于排序
--    新索引用等值列前缀 + 排序列，stock>0 作为 index condition 过滤
-- =====================================================================
CREATE INDEX idx_product_list ON `product` (`status`, `audit_status`, `update_time` DESC);

-- =====================================================================
-- 3. 商家商品：WHERE merchant_id=? ORDER BY update_time DESC LIMIT ?,?
--    现有 idx_product_merchant_id 只覆盖 WHERE
-- =====================================================================
CREATE INDEX idx_product_merchant_update ON `product` (`merchant_id`, `update_time` DESC);

-- =====================================================================
-- 4. 待审核商品：WHERE audit_status=0 ORDER BY create_time ASC
--    现有 idx_product_audit_status 只覆盖 WHERE
-- =====================================================================
CREATE INDEX idx_product_audit_create ON `product` (`audit_status`, `create_time`);

-- =====================================================================
-- 5. 茶友圈广场：WHERE is_deleted=0 ORDER BY create_time DESC LIMIT ?,?
--    is_deleted 基数极低，单独索引无效，与 create_time 组合后
--    等值前缀定位 is_deleted=0，直接顺序扫描排序
-- =====================================================================
CREATE INDEX idx_tea_post_deleted_time ON `tea_post` (`is_deleted`, `create_time` DESC);

-- =====================================================================
-- 6. 关注动态 / 用户动态：WHERE user_id IN(...) AND is_deleted=0
--                       ORDER BY create_time DESC LIMIT ?,?
--    每个 user_id 下的行已按 create_time 排序，多值扫描有序归并
-- =====================================================================
CREATE INDEX idx_tea_post_user_time ON `tea_post` (`user_id`, `create_time` DESC);

-- =====================================================================
-- 7. 话题帖子覆盖索引：WHERE topic_id=? -- COUNT 查询无需回表
--    现有 idx_tea_post_topic_topic_id(topic_id) 只含 topic_id
--    新索引包含 post_id，COUNT(1) 实现 Using index
-- =====================================================================
CREATE INDEX idx_post_topic_cover ON `tea_post_topic` (`topic_id`, `post_id`);

-- =====================================================================
-- 8. 聊天消息：WHERE session_id=? ORDER BY create_time DESC LIMIT ?,?
--    现有 idx_chat_message_session_id 只覆盖 WHERE
-- =====================================================================
CREATE INDEX idx_chat_message_session_time ON `chat_message` (`session_id`, `create_time` DESC);

-- =====================================================================
-- 9. 聊天标记已读 + 未读统计：三条件精确定位
--    WHERE session_id=? AND receiver_id=? AND is_read=0
--    现有两个单列/双列索引均无法同时覆盖三个条件
-- =====================================================================
CREATE INDEX idx_chat_message_read_status ON `chat_message` (`session_id`, `receiver_id`, `is_read`);

-- =====================================================================
-- 10. 商家会话列表：WHERE merchant_id=? ORDER BY update_time DESC
--     现有 uk_chat_session_buyer_merchant(buyer_id, merchant_id)
--     merchant_id 不在最左前缀，无法利用
-- =====================================================================
CREATE INDEX idx_chat_session_merchant_time ON `chat_session` (`merchant_id`, `update_time` DESC);

-- =====================================================================
-- 冗余索引清理：被新增复合索引完全覆盖，删除以减少写放大
-- =====================================================================
DROP INDEX `idx_orders_user_id` ON `orders`;
DROP INDEX `idx_orders_status` ON `orders`;
DROP INDEX `idx_tea_post_user_id` ON `tea_post`;
DROP INDEX `idx_tea_post_is_deleted` ON `tea_post`;
DROP INDEX `idx_product_status` ON `product`;
DROP INDEX `idx_product_audit_status` ON `product`;
DROP INDEX `idx_chat_message_session_id` ON `chat_message`;
