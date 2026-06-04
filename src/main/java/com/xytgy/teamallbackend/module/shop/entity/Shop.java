package com.xytgy.teamallbackend.module.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 商家店铺表
 */
@Data
@TableName("shop")
public class Shop {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的用户ID (商家账号)
     */
    private Long userId;

    /**
     * 店铺名称
     */
    private String shopName;

    /**
     * 营业执照图片URL或相关资质信息
     */
    private String businessLicense;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除 0否 1是
     */
    @TableLogic
    private Integer isDeleted;
}
