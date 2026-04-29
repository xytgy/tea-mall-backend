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
import com.xytgy.teamallbackend.module.teacircle.entity.TeaPostTopic;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaTopic;
import com.xytgy.teamallbackend.module.teacircle.repository.TeaLikeMapper;
import com.xytgy.teamallbackend.module.teacircle.repository.TeaPostMapper;
import com.xytgy.teamallbackend.module.teacircle.repository.TeaPostTopicMapper;
import com.xytgy.teamallbackend.module.teacircle.service.TeaCommentService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaFollowService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaNotificationService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaPostService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaTopicService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaPostVO;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeaPostServiceImpl extends ServiceImpl<TeaPostMapper, TeaPost> implements TeaPostService {

    private final UserService userService;
    private final TeaLikeMapper teaLikeMapper;
    private final TeaFollowService teaFollowService;
    private final TeaCommentService teaCommentService;
    private final TeaNotificationService teaNotificationService;
    private final TeaTopicService teaTopicService;
    private final TeaPostTopicMapper teaPostTopicMapper;
    private final ObjectMapper objectMapper;

    public TeaPostServiceImpl(
            UserService userService,
            TeaLikeMapper teaLikeMapper,
            TeaFollowService teaFollowService,
            @Lazy TeaCommentService teaCommentService,
            TeaNotificationService teaNotificationService,
            TeaTopicService teaTopicService,
            TeaPostTopicMapper teaPostTopicMapper,
            ObjectMapper objectMapper
    ) {
        this.userService = userService;
        this.teaLikeMapper = teaLikeMapper;
        this.teaFollowService = teaFollowService;
        this.teaCommentService = teaCommentService;
        this.teaNotificationService = teaNotificationService;
        this.teaTopicService = teaTopicService;
        this.teaPostTopicMapper = teaPostTopicMapper;
        this.objectMapper = objectMapper;
    }



    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public TeaPostVO addPost(Long userId, TeaPostAddRequest request) {
        if (request == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数错误");
        }
        if (!StringUtils.hasText(request.getContent()) && (request.getImages() == null || request.getImages().isEmpty())) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "内容和图片不能同时为空");
        }
        if (request.getImages() != null && request.getImages().size() > 9) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "最多上传 9 张图片");
        }
        if (request.getTopics() != null && request.getTopics().size() > 10) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "最多选择 10 个话题");
        }

        TeaPost post = new TeaPost();
        post.setUserId(userId);
        post.setContent(StringUtils.hasText(request.getContent()) ? request.getContent().trim() : null);
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            try {
                post.setImages(objectMapper.writeValueAsString(request.getImages()));
            } catch (Exception e) {
                throw new ServiceException(ResultCode.BAD_REQUEST, "图片参数不合法");
            }
        }
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setIsDeleted(0);
        this.save(post);
        
        if (request.getTopics() == null || request.getTopics().isEmpty()) {
            return toVO(post, userId);
        }
        
        Set<String> topicNames = new LinkedHashSet<>();
        for (String raw : request.getTopics()) {
            String n = normalizeTopicName(raw);
            if (n != null) {
                topicNames.add(n);
            }
        }
        
        for (String topicName : topicNames) {
            TeaTopic topic = teaTopicService.getOrCreateTopicByName(topicName, "#" + topicName);
            TeaPostTopic rel = new TeaPostTopic();
            rel.setPostId(post.getId());
            rel.setTopicId(topic.getId());
            try {
                teaPostTopicMapper.insert(rel);
            } catch (Exception e) {
                continue;
            }
            teaTopicService.lambdaUpdate()
                    .eq(TeaTopic::getId, topic.getId())
                    .setSql("post_count = post_count + 1")
                    .update();
        }

        return toVO(post, userId);
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
            return new PageResult<>(Collections.emptyList(), 0, page, pageSize);
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
    public PageResult<TeaPostVO> getTopicPosts(Long currentUserId, String topicName, int page, int pageSize) {
        TeaTopic topic = teaTopicService.getOne(new LambdaQueryWrapper<TeaTopic>().eq(TeaTopic::getName, topicName));
        if (topic == null) {
            return new PageResult<>(Collections.emptyList(), 0, page, pageSize);
        }
        
        long offset = (long) (Math.max(page, 1) - 1) * pageSize;
        long total = teaPostTopicMapper.countPostsByTopic(topic.getId());
        List<Long> postIds = teaPostTopicMapper.selectPostIdsByTopic(topic.getId(), offset, pageSize);
        if (postIds == null || postIds.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), total, page, pageSize);
        }
        
        List<TeaPost> posts = this.list(new LambdaQueryWrapper<TeaPost>()
                .in(TeaPost::getId, postIds)
                .eq(TeaPost::getIsDeleted, 0));
        
        Map<Long, TeaPost> postMap = posts.stream().collect(Collectors.toMap(TeaPost::getId, p -> p, (a, b) -> a));
        List<TeaPostVO> list = postIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .map(p -> toVO(p, currentUserId))
                .collect(Collectors.toList());
        
        return new PageResult<>(list, total, page, pageSize);
    }

    @Override
    public TeaPostVO getPostDetail(Long userId, Long postId) {
        TeaPost post = this.getById(postId);
        if (post == null || post.getIsDeleted() == 1) {
            throw new ServiceException(ResultCode.NOT_FOUND, "动态不存在");
        }
        TeaPostVO vo = toVO(post, userId);
        
        // Fetch recent comments for the detail view
        PageResult<com.xytgy.teamallbackend.module.teacircle.vo.TeaCommentVO> commentPage = teaCommentService.listComments(postId, 1, 20);
        vo.setComments(commentPage.getList());
        
        return vo;
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
        post.setDeleteTime(java.time.LocalDateTime.now());
        this.updateById(post);

        List<TeaPostTopic> rels = teaPostTopicMapper.selectList(new LambdaQueryWrapper<TeaPostTopic>()
                .eq(TeaPostTopic::getPostId, postId));
        if (rels != null && !rels.isEmpty()) {
            for (TeaPostTopic rel : rels) {
                teaTopicService.lambdaUpdate()
                        .eq(TeaTopic::getId, rel.getTopicId())
                        .setSql("post_count = IF(post_count > 0, post_count - 1, 0)")
                        .update();
            }
            teaPostTopicMapper.delete(new LambdaQueryWrapper<TeaPostTopic>().eq(TeaPostTopic::getPostId, postId));
        }
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
        return new PageResult<>(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    private TeaPostVO toVO(TeaPost post, Long currentUserId) {
        TeaPostVO vo = new TeaPostVO();
        BeanUtils.copyProperties(post, vo);
        vo.setCreateTime(post.getCreateTime() != null ? post.getCreateTime().format(FORMATTER) : null);
        vo.setUpdateTime(post.getUpdateTime() != null ? post.getUpdateTime().format(FORMATTER) : null);
        vo.setStatus(post.getIsDeleted() != null && post.getIsDeleted() == 0 ? 1 : 0);
        
        if (StringUtils.hasText(post.getImages())) {
            try {
                vo.setImages(objectMapper.readValue(post.getImages(), new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                vo.setImages(Arrays.asList(post.getImages().split(",")));
            }
        } else {
            vo.setImages(Collections.emptyList());
        }

        List<TeaPostTopic> rels = teaPostTopicMapper.selectList(new LambdaQueryWrapper<TeaPostTopic>()
                .eq(TeaPostTopic::getPostId, post.getId()));
        if (rels == null || rels.isEmpty()) {
            vo.setTopics(Collections.emptyList());
        } else {
            List<Long> topicIds = rels.stream().map(TeaPostTopic::getTopicId).distinct().collect(Collectors.toList());
            Map<Long, TeaTopic> topicMap = teaTopicService.listByIds(topicIds).stream()
                    .collect(Collectors.toMap(TeaTopic::getId, t -> t, (a, b) -> a));
            List<String> topics = rels.stream()
                    .map(TeaPostTopic::getTopicId)
                    .map(topicMap::get)
                    .filter(Objects::nonNull)
                    .map(t -> StringUtils.hasText(t.getTitle()) ? t.getTitle() : "#" + t.getName())
                    .distinct()
                    .collect(Collectors.toList());
            vo.setTopics(topics);
        }

        User u = userService.getById(post.getUserId());
        if (u != null) {
            com.xytgy.teamallbackend.module.teacircle.vo.AuthorVO author = new com.xytgy.teamallbackend.module.teacircle.vo.AuthorVO();
            author.setId(u.getId());
            author.setNickname(u.getNickname() != null ? u.getNickname() : u.getUserAccount());
            author.setAvatar(u.getAvatar());
            vo.setAuthor(author);
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

    private String normalizeTopicName(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith("#")) {
            s = s.substring(1).trim();
        }
        if (s.isEmpty()) {
            return null;
        }
        if (s.length() > 30) {
            s = s.substring(0, 30);
        }
        return s;
    }
}
