package com.xytgy.teamallbackend.module.teacircle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaFollow;
import com.xytgy.teamallbackend.module.teacircle.repository.TeaFollowMapper;
import com.xytgy.teamallbackend.module.teacircle.service.TeaFollowService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaNotificationService;
import com.xytgy.teamallbackend.module.teacircle.vo.SimpleUserVO;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeaFollowServiceImpl extends ServiceImpl<TeaFollowMapper, TeaFollow> implements TeaFollowService {

    @Autowired
    private UserService userService;
    @Autowired
    private TeaNotificationService teaNotificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Boolean> toggleFollow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "不能关注自己");
        }
        User targetUser = userService.getById(followingId);
        if (targetUser == null || targetUser.getIsDeleted() == 1) {
            throw new ServiceException(ResultCode.NOT_FOUND, "用户不存在");
        }

        LambdaQueryWrapper<TeaFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeaFollow::getFollowerId, followerId).eq(TeaFollow::getFollowingId, followingId);
        TeaFollow existing = this.getOne(wrapper);

        boolean isFollowing;
        if (existing != null) {
            this.removeById(existing.getId());
            isFollowing = false;
        } else {
            TeaFollow follow = new TeaFollow();
            follow.setFollowerId(followerId);
            follow.setFollowingId(followingId);
            this.save(follow);
            isFollowing = true;
            teaNotificationService.addNotification(followingId, "follow", follow.getId(), followerId);
        }

        Map<String, Boolean> res = new HashMap<>();
        res.put("isFollowing", isFollowing);
        return res;
    }

    @Override
    public PageResult<SimpleUserVO> getFollowingList(Long currentUserId, int page, int pageSize) {
        Page<TeaFollow> p = new Page<>(page, pageSize);
        this.page(p, new LambdaQueryWrapper<TeaFollow>()
                .eq(TeaFollow::getFollowerId, currentUserId)
                .orderByDesc(TeaFollow::getCreateTime));

        List<SimpleUserVO> records = p.getRecords().stream()
                .map(f -> toSimpleUserVO(f.getFollowingId(), currentUserId))
                .collect(Collectors.toList());
        return new PageResult<>(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public PageResult<SimpleUserVO> getFollowersList(Long currentUserId, int page, int pageSize) {
        Page<TeaFollow> p = new Page<>(page, pageSize);
        this.page(p, new LambdaQueryWrapper<TeaFollow>()
                .eq(TeaFollow::getFollowingId, currentUserId)
                .orderByDesc(TeaFollow::getCreateTime));

        List<SimpleUserVO> records = p.getRecords().stream()
                .map(f -> toSimpleUserVO(f.getFollowerId(), currentUserId))
                .collect(Collectors.toList());
        return new PageResult<>(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public boolean isFollowing(Long followerId, Long followingId) {
        if (followerId == null || followingId == null) return false;
        return this.count(new LambdaQueryWrapper<TeaFollow>()
                .eq(TeaFollow::getFollowerId, followerId)
                .eq(TeaFollow::getFollowingId, followingId)) > 0;
    }

    private SimpleUserVO toSimpleUserVO(Long targetUserId, Long currentUserId) {
        SimpleUserVO vo = new SimpleUserVO();
        User u = userService.getById(targetUserId);
        if (u != null) {
            vo.setId(u.getId());
            vo.setUserAccount(u.getUserAccount());
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
            vo.setBio("");
            vo.setIsFollowing(isFollowing(currentUserId, targetUserId));
        }
        return vo;
    }
}
