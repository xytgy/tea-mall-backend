SET @v13_last_error_cnt = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'mq_retry_record'
      AND column_name = 'last_error'
);
SET @v13_last_error_sql = IF(
    @v13_last_error_cnt = 0,
    "ALTER TABLE `mq_retry_record` ADD COLUMN `last_error` VARCHAR(1000) DEFAULT NULL COMMENT '最近一次发送或重试失败原因' AFTER `delay_level`",
    'SELECT 1'
);
PREPARE stmt FROM @v13_last_error_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
