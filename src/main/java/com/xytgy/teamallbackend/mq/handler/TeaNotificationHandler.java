package com.xytgy.teamallbackend.mq.handler;

import com.xytgy.teamallbackend.module.teacircle.service.TeaNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TeaNotificationHandler {

    private final TeaNotificationService teaNotificationService;

    public void handle(Long targetUserId, String type, Long sourceId, Long actorId) {
        teaNotificationService.addNotification(targetUserId, type, sourceId, actorId);
        log.info("茶友圈通知创建成功: targetUserId={}, type={}, actorId={}", targetUserId, type, actorId);
    }
}
