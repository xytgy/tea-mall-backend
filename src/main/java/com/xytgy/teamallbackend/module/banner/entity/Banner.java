package com.xytgy.teamallbackend.module.banner.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @TableName banner
 */
@TableName(value = "banner")
@Data
public class Banner {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 轮播图标题
     */
    private String title;

    /**
     * 轮播图图片URL
     */
    private String imageUrl;

    /**
     * 点击跳转链接
     */
    private String linkUrl;

    /**
     * 排序，越大越靠前
     */
    private Integer sortOrder;

    /**
     * 状态 1启用 0禁用
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
