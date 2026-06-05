package com.xytgy.teamallbackend.module.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.module.order.entity.OrderItem;
import com.xytgy.teamallbackend.module.order.service.OrderItemService;
import com.xytgy.teamallbackend.module.order.mapper.OrderItemMapper;
import org.springframework.stereotype.Service;

/**
* @author xytgy
* @description 针对表【order_item】的数据库操作Service实现
* @createDate 2026-04-15 08:01:10
*/
@Service
public class OrderItemServiceImpl extends ServiceImpl<OrderItemMapper, OrderItem>
    implements OrderItemService{

}




