package com.xytgy.teamallbackend.module.chat.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xytgy.teamallbackend.module.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}