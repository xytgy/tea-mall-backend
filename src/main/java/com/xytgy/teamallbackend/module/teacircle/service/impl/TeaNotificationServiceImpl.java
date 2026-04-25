package com.xytgy.teamallbackend.module.teacircle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaNotification;
import com.xytgy.teamallbackend.module.teacircle.repository.TeaNotificationMapper;
import com.xytgy.teamallbackend.module.teacircle.service.TeaNotificationService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaNotificationVO;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.user.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeaNotificationServiceImpl extends ServiceImpl<TeaNotificationMapper, TeaNotification> implements TeaNotificationService {

    @Autowired
    private UserService userService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Map<String, Integer> getUnreadCount(Long userId) {
        long count = this.count(new LambdaQueryWrapper<TeaNotification>()
                .eq(TeaNotification::getUserId, userId)
                .eq(TeaNotification::getIsRead, 0));
        Map<String, Integer> res = new HashMap<>();
        res.put("count", (int) count);
        return res;
    }

    @Override
    public PageResult<TeaNotificationVO> listNotifications(Long userId, int page, int pageSize) {
        Page<TeaNotification> p = new Page<>(page, pageSize);
        this.page(p, new LambdaQueryWrapper<TeaNotification>()
                .eq(TeaNotification::getUserId, userId)
                .orderByDesc(TeaNotification::getCreateTime));

        List<TeaNotificationVO> records = p.getRecords().stream().map(n -> {
            TeaNotificationVO vo = new TeaNotificationVO();
            BeanUtils.copyProperties(n, vo);
            vo.setCreateTime(n.getCreateTime() != null ? n.getCreateTime().format(FORMATTER) : null);
            User actor = userService.getById(n.getActorId());
            if (actor != null) {
                com.xytgy.teamallbackend.module.teacircle.vo.AuthorVO authorVO = new com.xytgy.teamallbackend.module.teacircle.vo.AuthorVO();
                authorVO.setId(actor.getId());
                authorVO.setNickname(actor.getNickname() != null ? actor.getNickname() : actor.getUserAccount());
                authorVO.setAvatar(actor.getAvatar());
                vo.setActor(authorVO);
            }
            // 可以根据 sourceId 查具体的点赞/评论内容，由于篇幅暂略，返回基本类型
            vo.setSourceContent("新" + n.getType() + "通知");
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public void markAsRead(Long userId) {
        this.lambdaUpdate()
                .eq(TeaNotification::getUserId, userId)
                .eq(TeaNotification::getIsRead, 0)
                .set(TeaNotification::getIsRead, 1)
                .update();
    }

    @Override
    public void addNotification(Long userId, String type, Long sourceId, Long actorId) {
        TeaNotification n = new TeaNotification();
        n.setUserId(userId);
        n.setType(type);
        n.setSourceId(sourceId);
        n.setActorId(actorId);
        n.setIsRead(0);
        this.save(n);
    }
}
