package com.xytgy.teamallbackend.module.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.xytgy.teamallbackend.module.chat.entity.ChatMessage;
import com.xytgy.teamallbackend.module.chat.entity.ChatSession;
import com.xytgy.teamallbackend.module.chat.mapper.ChatMessageMapper;
import com.xytgy.teamallbackend.module.chat.mapper.ChatSessionMapper;
import com.xytgy.teamallbackend.module.chat.service.ChatService;
import com.xytgy.teamallbackend.module.chat.vo.ChatMessageVO;
import com.xytgy.teamallbackend.module.chat.vo.ChatSessionVO;
import com.xytgy.teamallbackend.common.UserRole;
import com.xytgy.teamallbackend.config.datasource.ReadOnly;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.shop.service.ShopService;
import com.xytgy.teamallbackend.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatSessionMapper chatSessionMapper;
    
    private final UserMapper userMapper;
    
    private final ShopService shopService;

    private ChatSession getOrCreateSession(Long buyerId, Long merchantId) {
        ChatSession session = ChainWrappers.lambdaQueryChain(chatSessionMapper)
                .eq(ChatSession::getBuyerId, buyerId)
                .eq(ChatSession::getMerchantId, merchantId)
                .one();
        if (session == null) {
            session = new ChatSession();
            session.setBuyerId(buyerId);
            session.setMerchantId(merchantId);
            session.setLastTime(LocalDateTime.now());
            chatSessionMapper.insert(session);
        }
        return session;
    }




    @ReadOnly
    @Override
    public List<ChatMessageVO> listMessages(Long buyerId, Long merchantId, Integer page, Integer size) {
        ChatSession session = getOrCreateSession(buyerId, merchantId);
        Page<ChatMessage> chatPage = this.lambdaQuery()
                .eq(ChatMessage::getSessionId, session.getId())
                .orderByDesc(ChatMessage::getCreateTime)
                .page(new Page<>(page != null ? page : 1, size != null ? size : 20));

        List<ChatMessageVO> voList = chatPage.getRecords().stream().map(c->{
            Long displaySenderId = c.getSenderId();
            if (!displaySenderId.equals(buyerId)) {
                displaySenderId = merchantId;
            }
            Long displayReceiverId = c.getReceiverId();
            if (!displayReceiverId.equals(buyerId)) {
                displayReceiverId = merchantId;
            }
            return ChatMessageVO.builder()
                    .id(c.getId())
                    .sessionId(session.getId())
                    .senderId(displaySenderId)
                    .receiverId(displayReceiverId)
                    .content(c.getContent())
                    .msgType(c.getMsgType())
                    .isRead(c.getIsRead())
                    .createTime(c.getCreateTime()!= null ? c.getCreateTime().format(TIME_FORMATTER) : null)
                    .build();
        }).collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(voList);
        return voList;
    }
    private String resolveBuyerName(User buyer) {
        if (buyer == null) {
            return "未知用户";
        }
        if (buyer.getNickname() != null) {
            return buyer.getNickname();
        }
        return buyer.getUserAccount();
    }

    @Override
    public void markAsRead(Long buyerId, Long merchantId, Long readerId) {
        ChatSession session = getOrCreateSession(buyerId, merchantId);
        
        // 标记所有发给我的、且在当前session中的消息为已读
        QueryWrapper<ChatMessage> qw = new QueryWrapper<>();
        qw.eq("session_id", session.getId())
          .eq("receiver_id", readerId)
          .eq("is_read", 0);
          
        ChatMessage updateMsg = new ChatMessage();
        updateMsg.setIsRead(1);
        
        this.update(updateMsg, qw);
    }

    @ReadOnly
    @Override
    public List<ChatSessionVO> listSessions(Long merchantId) {
        QueryWrapper<ChatSession> qw = new QueryWrapper<>();
        qw.eq("merchant_id", merchantId).orderByDesc("update_time");
        List<ChatSession> sessions = chatSessionMapper.selectList(qw);
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> buyerIds = sessions.stream().map(ChatSession::getBuyerId).collect(Collectors.toSet());
        Map<Long, User> buyerMap = userMapper.selectBatchIds(buyerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Set<Long> sessionIds = sessions.stream().map(ChatSession::getId).collect(Collectors.toSet());
        // 只查 sessionId 列（覆盖索引），替代查全部列再 Java 端 groupingBy
        Map<Long, Long> unreadMap = this.lambdaQuery()
                .select(ChatMessage::getSessionId)
                .in(ChatMessage::getSessionId, sessionIds)
                .eq(ChatMessage::getReceiverId, merchantId)
                .eq(ChatMessage::getIsRead, 0)
                .list()
                .stream()
                .collect(Collectors.groupingBy(ChatMessage::getSessionId, Collectors.counting()));

        return sessions.stream().map(s -> {
            User buyer = buyerMap.get(s.getBuyerId());
            return ChatSessionVO.builder()
                    .buyerId(s.getBuyerId())
                    .buyerName(resolveBuyerName(buyer))
                    .buyerAvatar(buyer != null ? buyer.getAvatar() : null)
                    .lastMessage(s.getLastMessage())
                    .lastTime(s.getLastTime() != null ? s.getLastTime().format(TIME_FORMATTER) : null)
                    .unreadCount(unreadMap.getOrDefault(s.getId(), 0L).intValue())
                    .build();
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO saveMessage(Long senderId, Long receiverId, String content, Integer msgType) {
        // 判断谁是buyer，谁是merchant。实际业务中可能需要查user表确定身份
        // 为简单起见，如果当前senderId是买家，则receiverId是merchant，反之亦然。
        // 这里可以查一下receiverId是不是merchant
        User sender = userMapper.selectById(senderId);
        User receiver = userMapper.selectById(receiverId);
        
        Long buyerId = senderId;
        Long merchantId = receiverId;
        
        if (sender.getRole() != null && sender.getRole() == UserRole.MERCHANT.getCode()) {
            // sender是商家，获取对应的 shopId
            Long shopId = shopService.getShopIdByUserId(senderId);
            merchantId = shopId != null ? shopId : senderId;
            buyerId = receiverId;
        } else if (receiver != null && receiver.getRole() != null && receiver.getRole() == UserRole.MERCHANT.getCode()) {
            // receiver是商家，获取对应的 shopId
            Long shopId = shopService.getShopIdByUserId(receiverId);
            merchantId = shopId != null ? shopId : receiverId;
            buyerId = senderId;
        }
        
        ChatSession session = getOrCreateSession(buyerId, merchantId);
        
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setMsgType(msgType != null ? msgType : 0);
        message.setIsRead(0);
        message.setCreateTime(LocalDateTime.now());
        
        this.save(message);
        
        // 更新会话最新消息
        session.setLastMessage(content);
        session.setLastTime(message.getCreateTime());
        session.setUpdateTime(message.getCreateTime());
        chatSessionMapper.updateById(session);
        
        // 同样在保存时，如果是商家发的消息，把返回值的 senderId 修改为 shopId
        Long displaySenderId = message.getSenderId();
        if (sender.getRole() != null && sender.getRole() == UserRole.MERCHANT.getCode()) {
            displaySenderId = merchantId;
        }
        Long displayReceiverId = message.getReceiverId();
        if (receiver != null && receiver.getRole() != null && receiver.getRole() == UserRole.MERCHANT.getCode()) {
            displayReceiverId = merchantId;
        }
        
        return ChatMessageVO.builder()
                .id(message.getId())
                .sessionId(message.getSessionId())
                .senderId(displaySenderId)
                .receiverId(displayReceiverId)
                .content(message.getContent())
                .msgType(message.getMsgType())
                .isRead(message.getIsRead())
                .createTime(message.getCreateTime().format(TIME_FORMATTER))
                .build();
    }
}
