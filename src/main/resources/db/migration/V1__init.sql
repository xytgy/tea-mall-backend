    CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `useraccount` VARCHAR(50) NOT NULL COMMENT '账号',
  `password` VARCHAR(100) NOT NULL COMMENT '加密密码',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `role` TINYINT NOT NULL COMMENT '角色 0用户 1商家 2管理员',
  `status` TINYINT DEFAULT '1' COMMENT '状态 0禁用 1正常 2审核中',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像',
  `gender` TINYINT DEFAULT NULL COMMENT '性别 0未知 1男 2女',
  `is_deleted` TINYINT DEFAULT '0' COMMENT '逻辑删除 0否 1是',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_useraccount` (`useraccount`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE `user_address` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话',
  `receiver_address` VARCHAR(255) NOT NULL COMMENT '详细收货地址',
  `is_default` TINYINT(1) DEFAULT '0' COMMENT '是否默认地址 0:否 1:是',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_address_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收货地址表';

CREATE TABLE `shop` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '关联的用户ID(商家账号)',
  `shop_name` VARCHAR(255) NOT NULL COMMENT '店铺名称',
  `business_license` VARCHAR(500) DEFAULT NULL COMMENT '营业执照图片URL',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shop_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家店铺表';

CREATE TABLE `product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(100) NOT NULL COMMENT '商品名称',
  `description` TEXT COMMENT '商品描述',
  `image_url` VARCHAR(255) DEFAULT NULL COMMENT '商品图片URL',
  `price` DECIMAL(10,2) NOT NULL COMMENT '价格',
  `stock` INT NOT NULL COMMENT '库存',
  `merchant_id` BIGINT NOT NULL COMMENT '商家ID',
  `status` TINYINT DEFAULT '1' COMMENT '状态 0下架 1上架 2审核中',
  `category` VARCHAR(50) DEFAULT NULL COMMENT '分类',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `audit_status` INT DEFAULT '0' COMMENT '审核状态 0待审核 1已通过 2已驳回',
  `sales` INT DEFAULT '0' COMMENT '销量',
  PRIMARY KEY (`id`),
  KEY `idx_product_merchant_id` (`merchant_id`),
  KEY `idx_product_status` (`status`),
  KEY `idx_product_audit_status` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

CREATE TABLE `product_review` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `user_id` BIGINT NOT NULL COMMENT '评价用户ID',
  `rating` INT NOT NULL DEFAULT '5' COMMENT '评分(1-5)',
  `content` TEXT COMMENT '评价内容',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_review_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表';

CREATE TABLE `cart` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `quantity` INT NOT NULL COMMENT '数量',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_cart_user_id` (`user_id`),
  KEY `idx_cart_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

CREATE TABLE `orders` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` VARCHAR(50) NOT NULL COMMENT '订单号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '总金额',
  `status` TINYINT NOT NULL COMMENT '状态 0待支付 1已支付 2已发货 3已完成 4已取消',
  `receiver_name` VARCHAR(50) DEFAULT NULL COMMENT '收货人',
  `receiver_phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `receiver_address` VARCHAR(255) DEFAULT NULL COMMENT '地址',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
  `refusal_reason` VARCHAR(255) DEFAULT NULL COMMENT '商家拒绝退款原因',
  `payment_id` BIGINT DEFAULT NULL,
  `commission_rate` DECIMAL(5,4) DEFAULT '0.0500',
  `fee_base_amount` DECIMAL(10,2) DEFAULT NULL,
  `platform_fee` DECIMAL(10,2) DEFAULT NULL,
  `merchant_amount` DECIMAL(10,2) DEFAULT NULL,
  `settle_status` TINYINT DEFAULT '0',
  `settle_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_order_no` (`order_no`),
  KEY `idx_orders_user_id` (`user_id`),
  KEY `idx_orders_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

CREATE TABLE `order_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `product_name` VARCHAR(100) NOT NULL COMMENT '商品名称（快照）',
  `product_price` DECIMAL(10,2) NOT NULL COMMENT '商品价格（快照）',
  `quantity` INT NOT NULL COMMENT '数量',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '小计金额',
  PRIMARY KEY (`id`),
  KEY `idx_order_item_order_id` (`order_id`),
  KEY `idx_order_item_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';

CREATE TABLE `payment_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` BIGINT NOT NULL COMMENT '关联业务订单 ID',
  `out_trade_no` VARCHAR(64) NOT NULL COMMENT '商户支付流水号',
  `trade_no` VARCHAR(64) DEFAULT NULL COMMENT '支付宝交易号',
  `pay_channel` VARCHAR(32) NOT NULL COMMENT '支付渠道',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '支付金额',
  `status` VARCHAR(16) NOT NULL COMMENT '状态：PAYING, PAID, CLOSED, FAILED, REFUNDED',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `pay_time` DATETIME DEFAULT NULL COMMENT '实际支付完成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_record_out_trade_no` (`out_trade_no`),
  KEY `idx_payment_record_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水表';

CREATE TABLE `favorite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `product_id` BIGINT NOT NULL COMMENT '商品ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表';

CREATE TABLE `feedback` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '反馈ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '提交人ID (如用户未登录或游客，可为空)',
  `type` VARCHAR(50) NOT NULL COMMENT '反馈类型 (功能建议, 内容错误, 界面美化, 其他问题)',
  `content` VARCHAR(1000) NOT NULL COMMENT '反馈内容详情',
  `images` JSON DEFAULT NULL COMMENT '上传的图片URL数组，最多3张',
  `contact` VARCHAR(100) DEFAULT NULL COMMENT '用户留下的联系方式(手机/邮箱等)',
  `status` TINYINT NOT NULL DEFAULT '0' COMMENT '处理状态 (0:未处理, 1:已处理)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  PRIMARY KEY (`id`),
  KEY `idx_feedback_create_time` (`create_time`),
  KEY `idx_feedback_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='意见反馈表';

CREATE TABLE `support_ticket` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '咨询记录ID',
  `user_id` BIGINT NOT NULL COMMENT '发起咨询的用户ID',
  `category` VARCHAR(50) NOT NULL COMMENT '咨询分类 (如: 订单问题, 产品咨询, 其他问题)',
  `title` VARCHAR(100) NOT NULL COMMENT '咨询标题',
  `content` VARCHAR(1000) NOT NULL COMMENT '咨询详细内容',
  `contact` VARCHAR(100) DEFAULT NULL COMMENT '用户联系方式(选填)',
  `status` VARCHAR(20) NOT NULL DEFAULT 'unread' COMMENT '状态: unread(未回复), replied(已回复), resolved(已解决)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `reply_content` VARCHAR(1000) DEFAULT NULL COMMENT '客服回复内容',
  `reply_time` DATETIME DEFAULT NULL COMMENT '客服回复时间',
  PRIMARY KEY (`id`),
  KEY `idx_support_ticket_user_id` (`user_id`),
  KEY `idx_support_ticket_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户咨询(工单)表';
