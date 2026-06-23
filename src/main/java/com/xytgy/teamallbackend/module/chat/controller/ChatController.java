package com.xytgy.teamallbackend.module.chat.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.security.SecurityUtils;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.chat.dto.ChatReadRequest;
import com.xytgy.teamallbackend.module.chat.dto.MerchantChatReadRequest;
import com.xytgy.teamallbackend.module.chat.dto.MerchantChatSendRequest;
import com.xytgy.teamallbackend.module.chat.service.ChatService;
import com.xytgy.teamallbackend.module.chat.vo.ChatMessageVO;
import com.xytgy.teamallbackend.module.chat.vo.ChatSessionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.xytgy.teamallbackend.module.shop.service.ShopService;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "客服聊天")
@SecurityRequirement(name = "BearerAuth")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    
    private final ShopService shopService;

    // ================== 买家端 ==================

    @GetMapping("/messages")
    @Operation(summary = "【买家端】获取历史聊天记录")
    public Result<List<ChatMessageVO>> getMessages(
            @RequestParam("merchantId") Long merchantId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Long buyerId = currentUserId();
        return Result.success(chatService.listMessages(buyerId, merchantId, page, size));
    }

    @PostMapping("/read")
    @Operation(summary = "【买家端】标记消息为已读")
    public Result<Void> markAsRead(@Validated @RequestBody ChatReadRequest request) {
        Long buyerId = currentUserId();
        chatService.markAsRead(buyerId, request.getMerchantId(), buyerId);
        return Result.success(null);
    }

    // ================== 商家端 ==================

    @PreAuthorize("hasRole('MERCHANT')")
    @GetMapping("/sessions")
    @Operation(summary = "【商家端】获取会话列表")
    public Result<List<ChatSessionVO>> getSessions() {
        Long merchantId = currentUserId();
        // 尝试从上下文中获取商家的真实 shopId (因为可能买家存消息时，传的 merchantId 是商铺 ID)
        Long shopId = shopService.getShopIdByUserId(merchantId);
        if (shopId != null) {
            merchantId = shopId;
        }
        return Result.success(chatService.listSessions(merchantId));
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @GetMapping("/merchant/messages")
    @Operation(summary = "【商家端】获取与特定买家的历史消息")
    public Result<List<ChatMessageVO>> getMerchantMessages(
            @RequestParam("buyerId") Long buyerId,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Long merchantId = currentUserId();
        Long shopId = shopService.getShopIdByUserId(merchantId);
        if (shopId != null) {
            merchantId = shopId;
        }
        return Result.success(chatService.listMessages(buyerId, merchantId, page, size));
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/merchant/read")
    @Operation(summary = "【商家端】标记消息为已读")
    public Result<Void> merchantMarkAsRead(@Validated @RequestBody MerchantChatReadRequest request) {
        Long merchantId = currentUserId();
        Long shopId = shopService.getShopIdByUserId(merchantId);
        if (shopId != null) {
            merchantId = shopId;
        }
        chatService.markAsRead(request.getBuyerId(), merchantId, merchantId);
        return Result.success(null);
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @PostMapping("/merchant/send")
    @Operation(summary = "【商家端】发送消息给买家")
    public Result<ChatMessageVO> merchantSendMessage(@Validated @RequestBody MerchantChatSendRequest request) {
        Long merchantId = currentUserId();
        Long shopId = shopService.getShopIdByUserId(merchantId);
        if (shopId != null) {
            merchantId = shopId;
        }
        ChatMessageVO message = chatService.saveMessage(merchantId, request.getReceiverId(), request.getContent(), request.getMsgType());
        return Result.success(message);
    }

    private Long currentUserId() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new ServiceException(ResultCode.UNAUTHORIZED, "未登录");
        }
        return userId;
    }
}
