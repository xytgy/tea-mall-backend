-- 用户表（user是H2保留字，必须加引号）
CREATE TABLE IF NOT EXISTS "user" (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    useraccount VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100),
    role TINYINT NOT NULL,
    status TINYINT DEFAULT 1,
    nickname VARCHAR(50),
    avatar VARCHAR(255),
    is_deleted TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 商品表
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128),
    description VARCHAR(255),
    image_url VARCHAR(255),
    price DECIMAL(10, 2),
    stock INT DEFAULT 0,
    merchant_id BIGINT,
    status INT DEFAULT 1,
    audit_status INT DEFAULT 0,
    sales INT DEFAULT 0,
    category VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0
);

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT,
    total_amount DECIMAL(10, 2),
    status INT DEFAULT 0,
    source INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted INT DEFAULT 0
);

-- 秒杀活动表
CREATE TABLE IF NOT EXISTS flash_sale (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    status TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0
);

-- 秒杀商品表
CREATE TABLE IF NOT EXISTS flash_sale_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flash_sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    flash_price DECIMAL(10, 2) NOT NULL,
    total_stock INT NOT NULL,
    sold_count INT DEFAULT 0,
    max_per_user INT DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 秒杀白名单表
CREATE TABLE IF NOT EXISTS flash_sale_whitelist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flash_sale_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 秒杀失败订单表
CREATE TABLE IF NOT EXISTS flash_sale_failed_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id VARCHAR(128) NOT NULL,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    flash_sale_id BIGINT NOT NULL,
    error_msg VARCHAR(500),
    retry_count INT DEFAULT 0,
    status TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 秒杀审计日志表
CREATE TABLE IF NOT EXISTS flash_sale_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flash_sale_id BIGINT,
    operator_id BIGINT,
    action VARCHAR(50) NOT NULL,
    detail VARCHAR(1000),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 秒杀补偿表
CREATE TABLE IF NOT EXISTS flash_sale_compensation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    failed_order_id BIGINT,
    user_id BIGINT,
    flash_sale_id BIGINT,
    compensation_type VARCHAR(20),
    amount DECIMAL(10, 2),
    status TINYINT DEFAULT 0,
    operator_id BIGINT,
    remark VARCHAR(500),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
