CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT,
    total_amount DECIMAL(10, 2),
    status INT,
    receiver_name VARCHAR(64),
    receiver_phone VARCHAR(32),
    receiver_address VARCHAR(255),
    create_time TIMESTAMP,
    pay_time TIMESTAMP,
    refusal_reason VARCHAR(255),
    payment_id BIGINT,
    commission_rate DECIMAL(10, 2),
    fee_base_amount DECIMAL(10, 2),
    platform_fee DECIMAL(10, 2),
    merchant_amount DECIMAL(10, 2),
    settle_status INT,
    settle_time TIMESTAMP,
    source INT,
    is_deleted INT DEFAULT 0,
    CONSTRAINT uk_orders_order_no UNIQUE (order_no)
);

CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128),
    description VARCHAR(255),
    image_url VARCHAR(255),
    price DECIMAL(10, 2),
    stock INT,
    merchant_id BIGINT,
    status INT,
    audit_status INT,
    sales INT,
    category VARCHAR(64),
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    is_deleted INT DEFAULT 0
);

CREATE TABLE flash_sale_failed_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id VARCHAR(128),
    user_id BIGINT,
    product_id BIGINT,
    flash_sale_id BIGINT,
    error_msg VARCHAR(1000),
    retry_count INT,
    status INT,
    create_time TIMESTAMP
);
