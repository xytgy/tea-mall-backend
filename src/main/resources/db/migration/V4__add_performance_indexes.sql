-- 性能优化：添加缺失索引

-- product表：优化 listAvailableProducts 查询 (status=1 AND audit_status=1 AND stock>0)
CREATE INDEX idx_product_available ON `product` (`status`, `audit_status`, `stock`);

-- product_review表：优化批量查询用户信息
CREATE INDEX idx_product_review_user_id ON `product_review` (`user_id`);

-- cart表：优化购物车查询和删除 (user_id + product_id 联合查询)
CREATE INDEX idx_cart_user_product ON `cart` (`user_id`, `product_id`);

-- orders表：优化用户订单列表查询 (user_id + status) 和统计查询
CREATE INDEX idx_orders_user_status ON `orders` (`user_id`, `status`);

-- orders表：优化按时间排序的查询
CREATE INDEX idx_orders_create_time ON `orders` (`create_time`);

-- tea_comment表：优化批量查询用户信息
CREATE INDEX idx_tea_comment_user_id ON `tea_comment` (`user_id`);

-- tea_notification表：优化查询未读通知 (user_id + is_read)
CREATE INDEX idx_tea_notification_user_read ON `tea_notification` (`user_id`, `is_read`);

-- chat_message表：优化查询未读消息 (receiver_id + is_read)
CREATE INDEX idx_chat_message_receiver_read ON `chat_message` (`receiver_id`, `is_read`);
