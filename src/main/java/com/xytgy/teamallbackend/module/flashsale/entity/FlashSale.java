package com.xytgy.teamallbackend.module.flashsale.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("flash_sale")
public class FlashSale {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 状态：0-未开始， 1-已开始，2-已结束*/
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @TableField("is_deleted")
    private Integer deleted;
}
