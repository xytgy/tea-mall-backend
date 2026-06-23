package com.xytgy.teamallbackend.module.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xytgy.teamallbackend.module.support.entity.SupportTicket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SupportTicketMapper extends BaseMapper<SupportTicket> {
}