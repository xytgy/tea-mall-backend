ALTER TABLE `tea_post`
    ADD COLUMN `delete_time` DATETIME NULL COMMENT '删除时间';

CREATE INDEX `idx_is_deleted` ON `tea_post` (`is_deleted`);

