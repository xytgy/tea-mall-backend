ALTER TABLE `tea_topic`
    ADD COLUMN `name` VARCHAR(64) NULL COMMENT '话题唯一名，不带#，如：春茶品鉴',
    ADD COLUMN `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

UPDATE `tea_topic`
SET `name` = TRIM(BOTH '#' FROM `title`)
WHERE `name` IS NULL OR `name` = '';

ALTER TABLE `tea_topic`
    ADD UNIQUE KEY `uk_name` (`name`);

UPDATE `tea_topic`
SET `view_count` = CAST(CAST(REPLACE(`view_count`, 'w', '') AS DECIMAL(10, 2)) * 10000 AS UNSIGNED)
WHERE `view_count` LIKE '%w';

ALTER TABLE `tea_topic`
    MODIFY COLUMN `view_count` BIGINT NOT NULL DEFAULT 0,
    MODIFY COLUMN `post_count` BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS `tea_post_topic` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `post_id` BIGINT NOT NULL,
  `topic_id` BIGINT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_topic` (`post_id`, `topic_id`),
  KEY `idx_topic_id` (`topic_id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动态-话题关联表';

