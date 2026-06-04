CREATE TABLE flash_sale_compensation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    failed_order_id BIGINT NOT NULL COMMENT '关联的失败订单ID',
    user_id BIGINT NOT NULL,
    flash_sale_id BIGINT NOT NULL,
    compensation_type VARCHAR(20) NOT NULL COMMENT 'COUPON:优惠券 MANUAL:人工处理',
    amount DECIMAL(10,2) DEFAULT 0 COMMENT '补偿金额（优惠券面额）',
    status TINYINT DEFAULT 0 COMMENT '0待发放 1已发放 2已使用 3已作废',
    operator_id BIGINT COMMENT '处理人ID（人工处理时）',
    remark VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_compensation_user (user_id),
    INDEX idx_compensation_failed_order (failed_order_id)
);
