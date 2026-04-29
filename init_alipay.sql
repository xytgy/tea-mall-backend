CREATE TABLE IF NOT EXISTS `payment_record` (
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
  UNIQUE KEY `uk_out_trade_no` (`out_trade_no`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付流水表';

-- 扩展 orders 表
ALTER TABLE `orders` 
ADD COLUMN IF NOT EXISTS `payment_id` BIGINT DEFAULT NULL COMMENT '关联最终成功的支付流水 ID',
ADD COLUMN IF NOT EXISTS `commission_rate` DECIMAL(5,4) DEFAULT 0.0500 COMMENT '抽佣比例快照',
ADD COLUMN IF NOT EXISTS `fee_base_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '抽佣基数',
ADD COLUMN IF NOT EXISTS `platform_fee` DECIMAL(10,2) DEFAULT NULL COMMENT '平台抽佣金额',
ADD COLUMN IF NOT EXISTS `merchant_amount` DECIMAL(10,2) DEFAULT NULL COMMENT '商家应结金额',
ADD COLUMN IF NOT EXISTS `settle_status` TINYINT DEFAULT 0 COMMENT '结算状态：0-未结算, 1-结算中, 2-已结算',
ADD COLUMN IF NOT EXISTS `settle_time` DATETIME DEFAULT NULL COMMENT '预计结算时间';