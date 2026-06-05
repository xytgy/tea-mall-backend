package com.xytgy.teamallbackend.module.teacircle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaFollow;
import com.xytgy.teamallbackend.module.teacircle.mapper.TeaFollowMapper;
import com.xytgy.teamallbackend.module.teacircle.service.TeaFollowService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaNotificationService;
import com.xytgy.teamallbackend.module.teacircle.vo.SimpleUserVO;
import com.xytgy.teamallbackend.module.teacircle.vo.UserProfileVO;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaPost;
import com.xytgy.teamallbackend.module.teacircle.mapper.TeaPostMapper;
import com.xytgy.teamallbackend.module.teacircle.mapper.TeaLikeMapper;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaLike;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TeaFollowServiceImpl extends ServiceImpl<TeaFollowMapper, TeaFollow> implements TeaFollowService {

    private final UserService userService;

    private final TeaNotificationService teaNotificationService;
    
    private final TeaPostMapper teaPostMapper;
    
    private final TeaLikeMapper teaLikeMapper;

    public TeaFollowServiceImpl(UserService userService, TeaNotificationService teaNotificationService, TeaPostMapper teaPostMapper, TeaLikeMapper teaLikeMapper) {
        this.userService = userService;
        this.teaNotificationService = teaNotificationService;
        this.teaPostMapper = teaPostMapper;
        this.teaLikeMapper = teaLikeMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Boolean> toggleFollow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "不能关注自己");
        }
        User targetUser = userService.getById(followingId);
        if (targetUser == null) {
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
    public UserProfileVO getUserProfile(Long currentUserId, Long targetUserId) {
        User targetUser = userService.getById(targetUserId);
        if (targetUser == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "用户不存在");
        }

        // 关注数
        long followingCount = this.count(new LambdaQueryWrapper<TeaFollow>()
                .eq(TeaFollow::getFollowerId, targetUserId));

        // 粉丝数
        long followersCount = this.count(new LambdaQueryWrapper<TeaFollow>()
                .eq(TeaFollow::getFollowingId, targetUserId));

        // 获赞数：统计该用户发布的所有动态被点赞的总数
        // 1. 找出该用户所有的动态 ID
        List<TeaPost> posts = teaPostMapper.selectList(new LambdaQueryWrapper<TeaPost>()
                .eq(TeaPost::getUserId, targetUserId)
                .select(TeaPost::getId));
        
        long likeReceivedCount = 0;
        if (posts != null && !posts.isEmpty()) {
            List<Long> postIds = posts.stream().map(TeaPost::getId).toList();
            likeReceivedCount = teaLikeMapper.selectCount(new LambdaQueryWrapper<TeaLike>()
                    .in(TeaLike::getPostId, postIds));
        }

        return UserProfileVO.builder()
                .id(targetUser.getId())
                .nickname(targetUser.getNickname() != null ? targetUser.getNickname() : targetUser.getUserAccount())
                .avatar(targetUser.getAvatar())
                .bio("") // 若有相关字段可填充
                .followingCount((int) followingCount)
                .followersCount((int) followersCount)
                .likeReceivedCount((int) likeReceivedCount)
                .isFollowing(currentUserId != null && isFollowing(currentUserId, targetUserId))
                .build();
    }

    @Override
    public PageResult<SimpleUserVO> getFollowingList(Long currentUserId, int page, int pageSize) {
        Page<TeaFollow> p = new Page<>(page, pageSize);
        this.page(p, new LambdaQueryWrapper<TeaFollow>()
                .eq(TeaFollow::getFollowerId, currentUserId)
                .orderByDesc(TeaFollow::getCreateTime));

        List<Long> targetUserIds = p.getRecords().stream()
                .map(TeaFollow::getFollowingId).toList();
        List<SimpleUserVO> records = batchBuildSimpleUserVO(targetUserIds, currentUserId);
        return new PageResult<>(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public PageResult<SimpleUserVO> getFollowersList(Long currentUserId, int page, int pageSize) {
        Page<TeaFollow> p = new Page<>(page, pageSize);
        this.page(p, new LambdaQueryWrapper<TeaFollow>()
                .eq(TeaFollow::getFollowingId, currentUserId)
                .orderByDesc(TeaFollow::getCreateTime));

        List<Long> targetUserIds = p.getRecords().stream()
                .map(TeaFollow::getFollowerId).toList();
        List<SimpleUserVO> records = batchBuildSimpleUserVO(targetUserIds, currentUserId);
        return new PageResult<>(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public boolean isFollowing(Long followerId, Long followingId) {
        if (followerId == null || followingId == null) return false;
        return this.count(new LambdaQueryWrapper<TeaFollow>()
                .eq(TeaFollow::getFollowerId, followerId)
                .eq(TeaFollow::getFollowingId, followingId)) > 0;
    }

    /**
     * 批量构建 SimpleUserVO，将 N+1 查询优化为 2 次批量查询
     */
    private List<SimpleUserVO> batchBuildSimpleUserVO(List<Long> targetUserIds, Long currentUserId) {
        if (targetUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量查用户信息（1次SQL）
        Map<Long, User> userMap = userService.listByIds(targetUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 批量查当前用户对这些人的关注状态（1次SQL）
        Set<Long> followingSet = this.list(new LambdaQueryWrapper<TeaFollow>()
                        .eq(TeaFollow::getFollowerId, currentUserId)
                        .in(TeaFollow::getFollowingId, targetUserIds))
                .stream()
                .map(TeaFollow::getFollowingId)
                .collect(Collectors.toSet());

        return targetUserIds.stream().map(targetId -> {
            User u = userMap.get(targetId);
            if (u == null) return null;
            SimpleUserVO vo = new SimpleUserVO();
            vo.setId(u.getId());
            vo.setUserAccount(u.getUserAccount());
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
            vo.setBio("");
            vo.setIsFollowing(followingSet.contains(targetId));
            return vo;
        }).filter(java.util.Objects::nonNull).toList();
    }
}
