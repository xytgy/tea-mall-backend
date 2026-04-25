package com.xytgy.teamallbackend.module.teacircle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.teacircle.dto.TeaPostAddRequest;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaFollow;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaLike;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaPost;
import com.xytgy.teamallbackend.module.teacircle.repository.TeaLikeMapper;
import com.xytgy.teamallbackend.module.teacircle.repository.TeaPostMapper;
import com.xytgy.teamallbackend.module.teacircle.service.TeaFollowService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaNotificationService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaPostService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaPostVO;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.user.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeaPostServiceImpl extends ServiceImpl<TeaPostMapper, TeaPost> implements TeaPostService {

    @Autowired
    private UserService userService;
    @Autowired
    private TeaLikeMapper teaLikeMapper;
    @Autowired
    private TeaFollowService teaFollowService;
    @Autowired
    private TeaNotificationService teaNotificationService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void addPost(Long userId, TeaPostAddRequest request) {
        TeaPost post = new TeaPost();
        post.setUserId(userId);
        post.setContent(request.getContent());
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            post.setImages(String.join(",", request.getImages()));
        }
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setIsDeleted(0);
        this.save(post);
    }

    @Override
    public PageResult<TeaPostVO> getExplorePosts(Long userId, int page, int pageSize) {
        Page<TeaPost> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<TeaPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeaPost::getIsDeleted, 0).orderByDesc(TeaPost::getCreateTime);
        this.page(p, wrapper);
        return buildPageResult(p, userId);
    }

    @Override
    public PageResult<TeaPostVO> getFollowingPosts(Long currentUserId, int page, int pageSize) {
        List<Long> followingIds = teaFollowService.lambdaQuery()
                .eq(TeaFollow::getFollowerId, currentUserId)
                .list().stream().map(TeaFollow::getFollowingId).collect(Collectors.toList());

        if (followingIds.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, pageSize, page);
        }

        Page<TeaPost> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<TeaPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeaPost::getIsDeleted, 0)
                .in(TeaPost::getUserId, followingIds)
                .orderByDesc(TeaPost::getCreateTime);
        this.page(p, wrapper);
        return buildPageResult(p, currentUserId);
    }

    @Override
    public PageResult<TeaPostVO> getUserPosts(Long currentUserId, Long targetUserId, int page, int pageSize) {
        Page<TeaPost> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<TeaPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeaPost::getIsDeleted, 0)
                .eq(TeaPost::getUserId, targetUserId)
                .orderByDesc(TeaPost::getCreateTime);
        this.page(p, wrapper);
        return buildPageResult(p, currentUserId);
    }

    @Override
    public TeaPostVO getPostDetail(Long userId, Long postId) {
        TeaPost post = this.getById(postId);
        if (post == null || post.getIsDeleted() == 1) {
            throw new ServiceException(ResultCode.NOT_FOUND, "动态不存在");
        }
        return toVO(post, userId);
    }

    @Override
    public void deletePost(Long userId, Long postId) {
        TeaPost post = this.getById(postId);
        if (post == null || post.getIsDeleted() == 1) {
            throw new ServiceException(ResultCode.NOT_FOUND, "动态不存在");
        }
        if (!post.getUserId().equals(userId)) {
            throw new ServiceException(ResultCode.FORBIDDEN, "无权删除此动态");
        }
        post.setIsDeleted(1);
        this.updateById(post);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> toggleLike(Long userId, Long postId) {
        TeaPost post = this.getById(postId);
        if (post == null || post.getIsDeleted() == 1) {
            throw new ServiceException(ResultCode.NOT_FOUND, "动态不存在");
        }

        LambdaQueryWrapper<TeaLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeaLike::getUserId, userId).eq(TeaLike::getPostId, postId);
        TeaLike existingLike = teaLikeMapper.selectOne(wrapper);

        boolean isLiked;
        int newCount = post.getLikeCount() == null ? 0 : post.getLikeCount();

        if (existingLike != null) {
            teaLikeMapper.deleteById(existingLike.getId());
            newCount = Math.max(0, newCount - 1);
            isLiked = false;
        } else {
            TeaLike like = new TeaLike();
            like.setUserId(userId);
            like.setPostId(postId);
            teaLikeMapper.insert(like);
            newCount++;
            isLiked = true;
            if (!userId.equals(post.getUserId())) {
                teaNotificationService.addNotification(post.getUserId(), "like", like.getId(), userId);
            }
        }
        post.setLikeCount(newCount);
        this.updateById(post);

        Map<String, Object> result = new HashMap<>();
        result.put("isLiked", isLiked);
        result.put("likeCount", newCount);
        return result;
    }

    private PageResult<TeaPostVO> buildPageResult(Page<TeaPost> p, Long currentUserId) {
        List<TeaPostVO> records = p.getRecords().stream()
                .map(post -> toVO(post, currentUserId))
                .collect(Collectors.toList());
        return new PageResult<>(records, p.getTotal(), p.getSize(), p.getCurrent());
    }

    private TeaPostVO toVO(TeaPost post, Long currentUserId) {
        TeaPostVO vo = new TeaPostVO();
        BeanUtils.copyProperties(post, vo);
        vo.setCreateTime(post.getCreateTime() != null ? post.getCreateTime().format(FORMATTER) : null);
        
        if (post.getImages() != null && !post.getImages().isEmpty()) {
            vo.setImages(Arrays.asList(post.getImages().split(",")));
        } else {
            vo.setImages(Collections.emptyList());
        }

        User u = userService.getById(post.getUserId());
        if (u != null) {
            vo.setUserAccount(u.getUserAccount());
            vo.setNickname(u.getNickname());
            vo.setAvatar(u.getAvatar());
        }

        if (currentUserId != null) {
            Long count = teaLikeMapper.selectCount(new LambdaQueryWrapper<TeaLike>()
                    .eq(TeaLike::getUserId, currentUserId).eq(TeaLike::getPostId, post.getId()));
            vo.setIsLiked(count > 0);
            vo.setIsFollowing(teaFollowService.isFollowing(currentUserId, post.getUserId()));
        } else {
            vo.setIsLiked(false);
            vo.setIsFollowing(false);
        }
        return vo;
    }
}
