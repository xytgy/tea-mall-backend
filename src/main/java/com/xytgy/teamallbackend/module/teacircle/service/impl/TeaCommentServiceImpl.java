package com.xytgy.teamallbackend.module.teacircle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.teacircle.dto.TeaCommentAddRequest;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaComment;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaPost;
import com.xytgy.teamallbackend.module.teacircle.mapper.TeaCommentMapper;
import com.xytgy.teamallbackend.module.teacircle.service.TeaCommentService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaPostService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaCommentVO;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.mq.message.teacircle.TeaNotificationMessage;
import com.xytgy.teamallbackend.mq.publisher.TeaNotificationPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TeaCommentServiceImpl extends ServiceImpl<TeaCommentMapper, TeaComment> implements TeaCommentService {

    private final UserService userService;

    private final TeaPostService teaPostService;

    private final TeaNotificationPublisher teaNotificationPublisher;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TeaCommentServiceImpl(TeaPostService teaPostService, UserService userService, TeaNotificationPublisher teaNotificationPublisher) {
        this.teaPostService = teaPostService;
        this.userService = userService;
        this.teaNotificationPublisher = teaNotificationPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeaCommentVO addComment(Long userId, Long postId, TeaCommentAddRequest request) {
        TeaPost post = teaPostService.getById(postId);
        if (post == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "动态不存在");
        }

        TeaComment comment = new TeaComment();
        comment.setUserId(userId);
        comment.setPostId(postId);
        comment.setContent(request.getContent());
        comment.setRootId(request.getRootId());
        comment.setParentId(request.getParentId());
        comment.setReplyToUserId(request.getReplyToUserId());
        this.save(comment);

        teaPostService.lambdaUpdate()
                .eq(TeaPost::getId, postId)
                .setSql("comment_count = COALESCE(comment_count, 0) + 1")
                .update();

        // 通过 MQ 异步创建通知，降低核心评论链路耦合
        Long notifyUserId = request.getReplyToUserId() != null ? request.getReplyToUserId() : post.getUserId();
        if (!userId.equals(notifyUserId)) {
            boolean published = teaNotificationPublisher.publishCommentNotification(
                    TeaNotificationMessage.builder()
                            .targetUserId(notifyUserId)
                            .type("comment")
                            .sourceId(comment.getId())
                            .actorId(userId)
                            .build()
            );
            if (!published) {
                log.warn("RocketMQ 不可用，消息未发送");
            }
        }

        Set<Long> userIds = new HashSet<>();
        userIds.add(userId);
        if (request.getReplyToUserId() != null) userIds.add(request.getReplyToUserId());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return toVO(comment, userMap);
    }

    @Override
    public PageResult<TeaCommentVO> listComments(Long postId, int page, int pageSize) {
        Page<TeaComment> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<TeaComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeaComment::getPostId, postId)
               .isNull(TeaComment::getRootId)
               .orderByAsc(TeaComment::getCreateTime);
        this.page(p, wrapper);

        List<TeaComment> rootComments = p.getRecords();
        if (rootComments.isEmpty()) {
            return new PageResult<>(Collections.emptyList(), 0, page, pageSize);
        }

        Set<Long> rootIds = rootComments.stream().map(TeaComment::getId).collect(Collectors.toSet());
        // 每个一级评论最多查50条子回复，避免热门帖子一次返回几千条
        List<TeaComment> allReplies = this.lambdaQuery()
                .in(TeaComment::getRootId, rootIds)
                .orderByAsc(TeaComment::getCreateTime)
                .last("LIMIT 500")
                .list();
        Map<Long, List<TeaComment>> replyMap = allReplies.stream()
                .collect(Collectors.groupingBy(TeaComment::getRootId));

        Set<Long> allUserIds = new HashSet<>();
        rootComments.forEach(c -> {
            allUserIds.add(c.getUserId());
            if (c.getReplyToUserId() != null) allUserIds.add(c.getReplyToUserId());
        });
        allReplies.forEach(c -> {
            allUserIds.add(c.getUserId());
            if (c.getReplyToUserId() != null) allUserIds.add(c.getReplyToUserId());
        });
        Map<Long, User> userMap = userService.listByIds(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<TeaCommentVO> records = rootComments.stream().map(c -> {
            TeaCommentVO vo = toVO(c, userMap);
            List<TeaComment> replies = replyMap.getOrDefault(c.getId(), Collections.emptyList());
            vo.setChildren(replies.stream().map(r -> toVO(r, userMap)).toList());
            return vo;
        }).toList();

        return new PageResult<>(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    private TeaCommentVO toVO(TeaComment comment, Map<Long, User> userMap) {
        TeaCommentVO vo = new TeaCommentVO();
        BeanUtils.copyProperties(comment, vo);
        vo.setCreateTime(comment.getCreateTime() != null ? comment.getCreateTime().format(FORMATTER) : null);

        User u = userMap.get(comment.getUserId());
        if (u != null) {
            com.xytgy.teamallbackend.module.teacircle.vo.AuthorVO author = new com.xytgy.teamallbackend.module.teacircle.vo.AuthorVO();
            author.setId(u.getId());
            author.setNickname(u.getNickname() != null ? u.getNickname() : u.getUserAccount());
            author.setAvatar(u.getAvatar());
            vo.setAuthor(author);
        }

        if (comment.getReplyToUserId() != null) {
            User target = userMap.get(comment.getReplyToUserId());
            if (target != null) {
                com.xytgy.teamallbackend.module.teacircle.vo.AuthorVO replyAuthor = new com.xytgy.teamallbackend.module.teacircle.vo.AuthorVO();
                replyAuthor.setId(target.getId());
                replyAuthor.setNickname(target.getNickname() != null ? target.getNickname() : target.getUserAccount());
                replyAuthor.setAvatar(target.getAvatar());
                vo.setReplyToUser(replyAuthor);
            }
        }

        return vo;
    }
}
