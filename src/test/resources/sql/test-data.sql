-- 测试用户
INSERT INTO "user" (id, useraccount, password, nickname, role) VALUES (17, 'testuser', 'password', '测试用户', 0);
INSERT INTO "user" (id, useraccount, password, nickname, role) VALUES (18, 'testuser2', 'password', '测试用户2', 0);

-- 测试商品
INSERT INTO product (id, name, price, stock, status) VALUES (9, '测试商品1', 100.00, 100, 1);
INSERT INTO product (id, name, price, stock, status) VALUES (10, '测试商品2', 200.00, 50, 1);

-- 测试秒杀活动（状态1=进行中，时间覆盖当前时间）
INSERT INTO flash_sale (id, title, start_time, end_time, status) VALUES (1, '测试秒杀活动', TIMESTAMPADD(HOUR, -1, CURRENT_TIMESTAMP), TIMESTAMPADD(HOUR, 1, CURRENT_TIMESTAMP), 1);
INSERT INTO flash_sale (id, title, start_time, end_time, status) VALUES (2, '测试秒杀活动2', TIMESTAMPADD(HOUR, -1, CURRENT_TIMESTAMP), TIMESTAMPADD(HOUR, 1, CURRENT_TIMESTAMP), 1);

-- 测试秒杀商品（活动1的商品9，库存100）
INSERT INTO flash_sale_product (id, flash_sale_id, product_id, flash_price, total_stock, sold_count, max_per_user) VALUES (1, 1, 9, 9.90, 100, 0, 1);

-- 测试秒杀商品（活动2的商品10，库存50）—— 用于测试商品不属于活动
INSERT INTO flash_sale_product (id, flash_sale_id, product_id, flash_price, total_stock, sold_count, max_per_user) VALUES (2, 2, 10, 19.90, 50, 0, 1);
