package com.xytgy.teamallbackend.module.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.chat.entity.ChatMessage;
import com.xytgy.teamallbackend.module.chat.vo.ChatSessionVO;
import com.xytgy.teamallbackend.module.chat.vo.ChatMessageVO;

import java.util.List;

public interface ChatService extends IService<ChatMessage> {
    List<ChatMessageVO> listMessages(Long buyerId, Long merchantId, Integer page, Integer size);
    void markAsRead(Long buyerId, Long merchantId, Long readerId);
    List<ChatSessionVO> listSessions(Long merchantId);
    ChatMessageVO saveMessage(Long senderId, Long receiverId, String content, Integer msgType);
}