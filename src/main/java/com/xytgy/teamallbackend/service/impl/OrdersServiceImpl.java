package com.xytgy.teamallbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.entity.Orders;
import com.xytgy.teamallbackend.service.OrdersService;
import com.xytgy.teamallbackend.mapper.OrdersMapper;
import org.springframework.stereotype.Service;

/**
* @author xytgy
* @description 针对表【orders】的数据库操作Service实现
* @createDate 2026-04-15 08:01:03
*/
@Service
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders>
    implements OrdersService{

}




