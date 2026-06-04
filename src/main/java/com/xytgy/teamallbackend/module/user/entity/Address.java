package com.xytgy.teamallbackend.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_address")
public class Address {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private Boolean isDefault;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 逻辑删除 0否 1是
     */
    @TableLogic
    private Integer isDeleted;
}
