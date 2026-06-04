CREATE TABLE flash_sale (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(100) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status TINYINT DEFAULT 0 COMMENT '0未开始 1进行中 2已结束',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0
);

CREATE TABLE flash_sale_product (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    flash_sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    flash_price DECIMAL(10,2) NOT NULL,
    total_stock INT NOT NULL,
    sold_count INT DEFAULT 0,
    max_per_user INT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE flash_sale_whitelist (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    flash_sale_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_whitelist (flash_sale_id, user_id)
);

CREATE TABLE flash_sale_failed_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    flash_sale_id BIGINT NOT NULL,
    error_msg VARCHAR(500),
    retry_count INT DEFAULT 0,
    status TINYINT DEFAULT 0 COMMENT '0待重试 1已修复 2人工处理',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE flash_sale_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    flash_sale_id BIGINT NOT NULL,
    operator_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    detail TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE orders ADD COLUMN source TINYINT DEFAULT 0 COMMENT '订单来源：0普通 1秒杀';
