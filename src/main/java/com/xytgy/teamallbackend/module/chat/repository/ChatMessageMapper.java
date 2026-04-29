package com.xytgy.teamallbackend.module.chat.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xytgy.teamallbackend.module.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}