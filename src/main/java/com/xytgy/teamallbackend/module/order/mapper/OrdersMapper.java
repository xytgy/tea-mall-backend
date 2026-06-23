package com.xytgy.teamallbackend.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xytgy.teamallbackend.module.order.entity.Orders;
import org.apache.ibatis.annotations.Param;

/**
* @author xytgy
* @description 针对表【orders】的数据库操作Mapper
* @createDate 2026-04-15 08:01:03
* @Entity com.xytgy.teamallbackend.module.order.entity.Orders
*/
public interface OrdersMapper extends BaseMapper<Orders> {

    /**
     * 商家订单分页查询：通过 SQL 子查询在数据库层面完成商品ID->订单ID的关联过滤
     *
     * @param page       分页参数
     * @param merchantId 商家ID
     * @return 分页结果
     */
    Page<Orders> selectMerchantOrderPage(Page<Orders> page, @Param("merchantId") Long merchantId);
}
