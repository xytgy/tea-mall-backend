package com.xytgy.teamallbackend.module.teacircle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaNotification;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaNotificationVO;

import java.util.Map;

public interface TeaNotificationService extends IService<TeaNotification> {
    Map<String, Integer> getUnreadCount(Long userId);
    PageResult<TeaNotificationVO> listNotifications(Long userId, int page, int pageSize);
    void markAsRead(Long userId);
    void addNotification(Long userId, String type, Long sourceId, Long actorId);
}
