package com.xytgy.teamallbackend.module.teacircle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.config.datasource.ReadOnly;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.teacircle.dto.TeaPostAddRequest;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaFollow;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaLike;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaPost;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaPostTopic;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaTopic;
import com.xytgy.teamallbackend.module.teacircle.mapper.TeaLikeMapper;
import com.xytgy.teamallbackend.module.teacircle.mapper.TeaPostMapper;
import com.xytgy.teamallbackend.module.teacircle.mapper.TeaPostTopicMapper;
import com.xytgy.teamallbackend.module.teacircle.service.TeaCommentService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaFollowService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaPostService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaTopicService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaPostVO;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.mq.message.teacircle.TeaNotificationMessage;
import com.xytgy.teamallbackend.mq.publisher.TeaNotificationPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TeaPostServiceImpl extends ServiceImpl<TeaPostMapper, TeaPost> implements TeaPostService {

    private final UserService userService;
    private final TeaLikeMapper teaLikeMapper;
    private final TeaFollowService teaFollowService;
    private final TeaCommentService teaCommentService;
    private final TeaTopicService teaTopicService;
    private final TeaPostTopicMapper teaPostTopicMapper;
    private final ObjectMapper objectMapper;
    private final TeaNotificationPublisher teaNotificationPublisher;

    public TeaPostServiceImpl(
            UserService userService,
            TeaLikeMapper teaLikeMapper,
            TeaFollowService teaFollowService,
            // TeaPostServiceImpl → TeaCommentService → TeaPostServiceImpl 循环依赖，@Lazy 延迟解析打破循环
            @Lazy TeaCommentService teaCommentService,
            TeaTopicService teaTopicService,
            TeaPostTopicMapper teaPostTopicMapper,
            ObjectMapper objectMapper,
            TeaNotificationPublisher teaNotificationPublisher
    ) {
        this.userService = userService;
        this.teaLikeMapper = teaLikeMapper;
        this.teaFollowService = teaFollowService;
        this.teaCommentService = teaCommentService;
        this.teaTopicService = teaTopicService;
        this.teaPostTopicMapper = teaPostTopicMapper;
        this.objectMapper = objectMapper;
        this.teaNotificationPublisher = teaNotificationPublisher;
    }



    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeaPostVO addPost(Long userId, TeaPostAddRequest request) {
        validatePostRequest(request);
        TeaPost post = createAndSavePost(userId, request);
        if (request.getTopics() != null && !request.getTopics().isEmpty()) {
            bindTopicsToPost(post.getId(), request.getTopics());
        }
        return toVO(post, userId);
    }

    private void validatePostRequest(TeaPostAddRequest request) {
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
    }

    private TeaPost createAndSavePost(Long userId, TeaPostAddRequest request) {
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
        this.save(post);
        return post;
    }

    private void bindTopicsToPost(Long postId, List<String> topics) {
        Set<String> topicNames = new LinkedHashSet<>();
        for (String raw : topics) {
            String name = normalizeTopicName(raw);
            if (name != null) {
                topicNames.add(name);
            }
        }
        for (String topicName : topicNames) {
            TeaTopic topic = teaTopicService.getOrCreateTopicByName(topicName, "#" + topicName);
            TeaPostTopic rel = new TeaPostTopic();
            rel.setPostId(postId);
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
    }

    @ReadOnly
    @Override
    public PageResult<TeaPostVO> getExplorePosts(Long userId, int page, int pageSize) {
        Page<TeaPost> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<TeaPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(TeaPost::getCreateTime);
        this.page(p, wrapper);
        return buildPageResult(p, userId);
    }

    @ReadOnly
    @Override
    public PageResult<TeaPostVO> getFollowingPosts(Long currentUserId, int page, int pageSize) {
        List<Long> followingIds = teaFollowService.lambdaQuery()
                .eq(TeaFollow::getFollowerId, currentUserId)
                .list().stream().map(TeaFollow::getFollowingId).toList();

        if (followingIds.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, page, pageSize);
        }

        Page<TeaPost> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<TeaPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(TeaPost::getUserId, followingIds)
                .orderByDesc(TeaPost::getCreateTime);
        this.page(p, wrapper);
        return buildPageResult(p, currentUserId);
    }

    @ReadOnly
    @Override
    public PageResult<TeaPostVO> getUserPosts(Long currentUserId, Long targetUserId, int page, int pageSize) {
        Page<TeaPost> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<TeaPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeaPost::getUserId, targetUserId)
                .orderByDesc(TeaPost::getCreateTime);
        this.page(p, wrapper);
        return buildPageResult(p, currentUserId);
    }
    
    @ReadOnly
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
                .in(TeaPost::getId, postIds));
        
        Map<Long, TeaPost> postMap = posts.stream().collect(Collectors.toMap(TeaPost::getId, p -> p, (a, b) -> a));
        List<TeaPostVO> list = postIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .map(p -> toVO(p, currentUserId))
                .toList();
        
        return new PageResult<>(list, total, page, pageSize);
    }

    @Override
    public TeaPostVO getPostDetail(Long userId, Long postId) {
        TeaPost post = this.getById(postId);
        if (post == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "动态不存在");
        }
        TeaPostVO vo = toVO(post, userId);
        
        // Fetch recent comments for the detail view
        PageResult<com.xytgy.teamallbackend.module.teacircle.vo.TeaCommentVO> commentPage = teaCommentService.listComments(postId, 1, 20);
        vo.setComments(commentPage.getList());
        
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long userId, Long postId) {
        TeaPost post = this.getById(postId);
        if (post == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "动态不存在");
        }
        if (!post.getUserId().equals(userId)) {
            throw new ServiceException(ResultCode.FORBIDDEN, "无权删除此动态");
        }
        // 使用@TableLogic注解后，removeById会自动执行逻辑删除
        this.removeById(postId);

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
        if (post == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "动态不存在");
        }

        LambdaQueryWrapper<TeaLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeaLike::getUserId, userId).eq(TeaLike::getPostId, postId);
        TeaLike existingLike = teaLikeMapper.selectOne(wrapper);

        boolean isLiked;
        if (existingLike != null) {
            teaLikeMapper.deleteById(existingLike.getId());
            // SQL 原子操作，避免并发下计数丢失
            this.lambdaUpdate()
                    .eq(TeaPost::getId, postId)
                    .setSql("like_count = GREATEST(like_count - 1, 0)")
                    .update();
            isLiked = false;
        } else {
            TeaLike like = new TeaLike();
            like.setUserId(userId);
            like.setPostId(postId);
            try {
                teaLikeMapper.insert(like);
            } catch (Exception e) {
                // 唯一索引冲突说明并发重复点赞，忽略即可
                Map<String, Object> dup = new HashMap<>();
                dup.put("isLiked", true);
                dup.put("likeCount", this.getById(postId).getLikeCount());
                return dup;
            }
            this.lambdaUpdate()
                    .eq(TeaPost::getId, postId)
                    .setSql("like_count = like_count + 1")
                    .update();
            isLiked = true;
            if (!userId.equals(post.getUserId())) {
                boolean published = teaNotificationPublisher.publishLikeNotification(
                        TeaNotificationMessage.builder()
                                .targetUserId(post.getUserId())
                                .type("like")
                                .sourceId(like.getId())
                                .actorId(userId)
                                .build()
                );
                if (!published) {
                    log.warn("RocketMQ 不可用，消息未发送");
                }
            }
        }

        TeaPost updated = this.getById(postId);
        Map<String, Object> result = new HashMap<>();
        result.put("isLiked", isLiked);
        result.put("likeCount", updated.getLikeCount());
        return result;
    }

    private PageResult<TeaPostVO> buildPageResult(Page<TeaPost> p, Long currentUserId) {
        List<TeaPost> posts = p.getRecords();
        if (posts.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, p.getCurrent(), p.getSize());
        }

        // 批量收集所有需要查询的 ID
        Set<Long> userIds = posts.stream().map(TeaPost::getUserId).collect(Collectors.toSet());
        List<Long> postIds = posts.stream().map(TeaPost::getId).toList();

        // 批量查询用户信息
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        // 批量查询当前用户的点赞状态
        Set<Long> likedPostIds = Collections.emptySet();
        Set<Long> followingUserIds = Collections.emptySet();
        if (currentUserId != null) {
            likedPostIds = teaLikeMapper.selectList(new LambdaQueryWrapper<TeaLike>()
                    .eq(TeaLike::getUserId, currentUserId)
                    .in(TeaLike::getPostId, postIds))
                    .stream().map(TeaLike::getPostId).collect(Collectors.toSet());

            followingUserIds = teaFollowService.lambdaQuery()
                    .eq(TeaFollow::getFollowerId, currentUserId)
                    .in(TeaFollow::getFollowingId, userIds)
                    .list().stream().map(TeaFollow::getFollowingId).collect(Collectors.toSet());
        }

        // 批量查询帖子-话题关联
        List<TeaPostTopic> allRels = teaPostTopicMapper.selectList(new LambdaQueryWrapper<TeaPostTopic>()
                .in(TeaPostTopic::getPostId, postIds));
        Map<Long, List<TeaPostTopic>> relMap = allRels.stream()
                .collect(Collectors.groupingBy(TeaPostTopic::getPostId));

        // 批量查询话题详情
        Set<Long> allTopicIds = allRels.stream().map(TeaPostTopic::getTopicId).collect(Collectors.toSet());
        Map<Long, TeaTopic> topicMap = allTopicIds.isEmpty() ? Collections.emptyMap()
                : teaTopicService.listByIds(allTopicIds).stream()
                        .collect(Collectors.toMap(TeaTopic::getId, t -> t, (a, b) -> a));

        // 组装 VO
        Set<Long> finalLikedPostIds = likedPostIds;
        Set<Long> finalFollowingUserIds = followingUserIds;
        List<TeaPostVO> records = posts.stream()
                .map(post -> toVO(post, currentUserId, userMap, finalLikedPostIds, finalFollowingUserIds, relMap, topicMap))
                .toList();

        return new PageResult<>(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    private TeaPostVO toVO(TeaPost post, Long currentUserId,
                           Map<Long, User> userMap,
                           Set<Long> likedPostIds,
                           Set<Long> followingUserIds,
                           Map<Long, List<TeaPostTopic>> relMap,
                           Map<Long, TeaTopic> topicMap) {
        TeaPostVO vo = new TeaPostVO();
        BeanUtils.copyProperties(post, vo);
        vo.setCreateTime(post.getCreateTime() != null ? post.getCreateTime().format(FORMATTER) : null);
        vo.setUpdateTime(post.getUpdateTime() != null ? post.getUpdateTime().format(FORMATTER) : null);
        vo.setStatus(1);
        vo.setImages(parseImageList(post.getImages()));
        vo.setTopics(resolveTopics(post.getId(), relMap, topicMap));
        populateAuthor(vo, post.getUserId(), userMap);
        vo.setIsLiked(currentUserId != null && likedPostIds.contains(post.getId()));
        vo.setIsFollowing(currentUserId != null && followingUserIds.contains(post.getUserId()));
        return vo;
    }

    private List<String> parseImageList(String imagesJson) {
        if (!StringUtils.hasText(imagesJson)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Arrays.asList(imagesJson.split(","));
        }
    }

    private List<String> resolveTopics(Long postId, Map<Long, List<TeaPostTopic>> relMap, Map<Long, TeaTopic> topicMap) {
        List<TeaPostTopic> rels = relMap.getOrDefault(postId, Collections.emptyList());
        if (rels.isEmpty()) {
            return Collections.emptyList();
        }
        return rels.stream()
                .map(r -> topicMap.get(r.getTopicId()))
                .filter(Objects::nonNull)
                .map(t -> StringUtils.hasText(t.getTitle()) ? t.getTitle() : "#" + t.getName())
                .distinct()
                .toList();
    }

    private void populateAuthor(TeaPostVO vo, Long userId, Map<Long, User> userMap) {
        User u = userMap.get(userId);
        if (u != null) {
            com.xytgy.teamallbackend.module.teacircle.vo.AuthorVO author = new com.xytgy.teamallbackend.module.teacircle.vo.AuthorVO();
            author.setId(u.getId());
            author.setNickname(u.getNickname() != null ? u.getNickname() : u.getUserAccount());
            author.setAvatar(u.getAvatar());
            vo.setAuthor(author);
        }
    }

    // 单条动态转换（用于 addPost、getPostDetail 等单条场景）
    private TeaPostVO toVO(TeaPost post, Long currentUserId) {
        Set<Long> userIds = Set.of(post.getUserId());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Set<Long> likedPostIds = Collections.emptySet();
        Set<Long> followingUserIds = Collections.emptySet();
        if (currentUserId != null) {
            likedPostIds = teaLikeMapper.selectList(new LambdaQueryWrapper<TeaLike>()
                    .eq(TeaLike::getUserId, currentUserId)
                    .eq(TeaLike::getPostId, post.getId()))
                    .stream().map(TeaLike::getPostId).collect(Collectors.toSet());
            followingUserIds = teaFollowService.lambdaQuery()
                    .eq(TeaFollow::getFollowerId, currentUserId)
                    .eq(TeaFollow::getFollowingId, post.getUserId())
                    .list().stream().map(TeaFollow::getFollowingId).collect(Collectors.toSet());
        }

        List<TeaPostTopic> rels = teaPostTopicMapper.selectList(new LambdaQueryWrapper<TeaPostTopic>()
                .eq(TeaPostTopic::getPostId, post.getId()));
        Map<Long, List<TeaPostTopic>> relMap = Map.of(post.getId(), rels);
        Set<Long> topicIds = rels.stream().map(TeaPostTopic::getTopicId).collect(Collectors.toSet());
        Map<Long, TeaTopic> topicMap = topicIds.isEmpty() ? Collections.emptyMap()
                : teaTopicService.listByIds(topicIds).stream()
                        .collect(Collectors.toMap(TeaTopic::getId, t -> t, (a, b) -> a));

        return toVO(post, currentUserId, userMap, likedPostIds, followingUserIds, relMap, topicMap);
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
