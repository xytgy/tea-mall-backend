package com.xytgy.teamallbackend.module.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.module.chat.entity.ChatMessage;
import com.xytgy.teamallbackend.module.chat.entity.ChatSession;
import com.xytgy.teamallbackend.module.chat.repository.ChatMessageMapper;
import com.xytgy.teamallbackend.module.chat.repository.ChatSessionMapper;
import com.xytgy.teamallbackend.module.chat.service.ChatService;
import com.xytgy.teamallbackend.module.chat.vo.ChatMessageVO;
import com.xytgy.teamallbackend.module.chat.vo.ChatSessionVO;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.shop.service.ShopService;
import com.xytgy.teamallbackend.module.user.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatSessionMapper chatSessionMapper;
    
    private final UserMapper userMapper;
    
    private final ShopService shopService;

    private ChatSession getOrCreateSession(Long buyerId, Long merchantId) {
        QueryWrapper<ChatSession> qw = new QueryWrapper<>();
        qw.eq("buyer_id", buyerId).eq("merchant_id", merchantId);
        ChatSession session = chatSessionMapper.selectOne(qw);
        if (session == null) {
            session = new ChatSession();
            session.setBuyerId(buyerId);
            session.setMerchantId(merchantId);
            session.setLastTime(LocalDateTime.now());
            chatSessionMapper.insert(session);
        }
        return session;
    }

    @Override
    public List<ChatMessageVO> listMessages(Long buyerId, Long merchantId, Integer page, Integer size) {
        ChatSession session = getOrCreateSession(buyerId, merchantId);

        Page<ChatMessage> pageParam = new Page<>(page != null ? page : 1, size != null ? size : 20);
        QueryWrapper<ChatMessage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("session_id", session.getId())
                .orderByDesc("create_time");

        Page<ChatMessage> chatPage = this.page(pageParam, queryWrapper);

        List<ChatMessageVO> voList = chatPage.getRecords().stream().map(c -> {
            // 【关键修复】如果消息的 senderId 属于商家的 userId，那么在返回给前端时，必须统一把它抹成 merchantId (shopId)
            // 否则前端在拿这条消息的 senderId 跟自己的身份对比时，会发现对不上，就把气泡渲染到左边（对方）去了。
            Long displaySenderId = c.getSenderId();
            if (!displaySenderId.equals(buyerId)) { // 如果发送方不是买家，那肯定是商家
                displaySenderId = merchantId;       // 强制把发送方 ID 显示为 shopId
            }
            
            Long displayReceiverId = c.getReceiverId();
            if (!displayReceiverId.equals(buyerId)) {
                displayReceiverId = merchantId;
            }

            return ChatMessageVO.builder()
                .id(c.getId())
                .sessionId(c.getSessionId())
                .senderId(displaySenderId)
                .receiverId(displayReceiverId)
                .content(c.getContent())
                .msgType(c.getMsgType())
                .isRead(c.getIsRead())
                .createTime(c.getCreateTime() != null ? c.getCreateTime().format(TIME_FORMATTER) : null)
                .build();
        }).collect(Collectors.toList());

        Collections.reverse(voList);
        return voList;
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

    @Override
    public List<ChatSessionVO> listSessions(Long merchantId) {
        QueryWrapper<ChatSession> qw = new QueryWrapper<>();
        qw.eq("merchant_id", merchantId).orderByDesc("update_time");
        List<ChatSession> sessions = chatSessionMapper.selectList(qw);
        
        return sessions.stream().map(s -> {
            User buyer = userMapper.selectById(s.getBuyerId());
            
            // 计算未读消息数
            QueryWrapper<ChatMessage> unreadQw = new QueryWrapper<>();
            unreadQw.eq("session_id", s.getId())
                    .eq("receiver_id", merchantId)
                    .eq("is_read", 0);
            long unreadCount = this.count(unreadQw);
            
            return ChatSessionVO.builder()
                    .buyerId(s.getBuyerId())
                    .buyerName(buyer != null ? (buyer.getNickname() != null ? buyer.getNickname() : buyer.getUserAccount()) : "未知用户")
                    .buyerAvatar(buyer != null ? buyer.getAvatar() : null)
                    .lastMessage(s.getLastMessage())
                    .lastTime(s.getLastTime() != null ? s.getLastTime().format(TIME_FORMATTER) : null)
                    .unreadCount((int)unreadCount)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public ChatMessageVO saveMessage(Long senderId, Long receiverId, String content, Integer msgType) {
        // 判断谁是buyer，谁是merchant。实际业务中可能需要查user表确定身份
        // 为简单起见，如果当前senderId是买家，则receiverId是merchant，反之亦然。
        // 这里可以查一下receiverId是不是merchant
        User sender = userMapper.selectById(senderId);
        User receiver = userMapper.selectById(receiverId);
        
        Long buyerId = senderId;
        Long merchantId = receiverId;
        
        if (sender.getRole() != null && sender.getRole() == 1) {
            // sender是商家，获取对应的 shopId
            Long shopId = shopService.getShopIdByUserId(senderId);
            merchantId = shopId != null ? shopId : senderId;
            buyerId = receiverId;
        } else if (receiver != null && receiver.getRole() != null && receiver.getRole() == 1) {
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
        if (sender.getRole() != null && sender.getRole() == 1) {
            displaySenderId = merchantId;
        }
        Long displayReceiverId = message.getReceiverId();
        if (receiver != null && receiver.getRole() != null && receiver.getRole() == 1) {
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
