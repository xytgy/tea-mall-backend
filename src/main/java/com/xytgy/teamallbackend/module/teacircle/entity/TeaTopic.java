package com.xytgy.teamallbackend.module.teacircle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tea_topic")
public class TeaTopic {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String title;
    private String description;
    private Long viewCount;
    private Long postCount;
    private Integer isHot; // 1:热门, 0:普通
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 逻辑删除 0否 1是
     */
    @TableLogic
    private Integer isDeleted;
}
