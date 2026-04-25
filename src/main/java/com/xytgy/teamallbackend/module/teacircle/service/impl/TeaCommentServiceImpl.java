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
import com.xytgy.teamallbackend.module.teacircle.repository.TeaCommentMapper;
import com.xytgy.teamallbackend.module.teacircle.service.TeaCommentService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaNotificationService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaPostService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaCommentVO;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.user.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeaCommentServiceImpl extends ServiceImpl<TeaCommentMapper, TeaComment> implements TeaCommentService {

    private final UserService userService;
    private final TeaPostService teaPostService;
    private final TeaNotificationService teaNotificationService;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TeaCommentServiceImpl(TeaNotificationService teaNotificationService, TeaPostService teaPostService, UserService userService) {
        this.teaNotificationService = teaNotificationService;
        this.teaPostService = teaPostService;
        this.userService = userService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeaCommentVO addComment(Long userId, Long postId, TeaCommentAddRequest request) {
        TeaPost post = teaPostService.getById(postId);
        if (post == null || post.getIsDeleted() == 1) {
            throw new ServiceException(ResultCode.NOT_FOUND, "动态不存在");
        }

        TeaComment comment = new TeaComment();
        comment.setUserId(userId);
        comment.setPostId(postId);
        comment.setContent(request.getContent());
        comment.setRootId(request.getRootId());
        comment.setParentId(request.getParentId());
        comment.setReplyToUserId(request.getReplyToUserId());
        comment.setIsDeleted(0);
        this.save(comment);

        // Update post comment count
        int newCount = (post.getCommentCount() == null ? 0 : post.getCommentCount()) + 1;
        post.setCommentCount(newCount);
        teaPostService.updateById(post);

        // Notify
        Long notifyUserId = request.getReplyToUserId() != null ? request.getReplyToUserId() : post.getUserId();
        if (!userId.equals(notifyUserId)) {
            teaNotificationService.addNotification(notifyUserId, "comment", comment.getId(), userId);
        }

        return toVO(comment);
    }

    @Override
    public PageResult<TeaCommentVO> listComments(Long postId, int page, int pageSize) {
        Page<TeaComment> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<TeaComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeaComment::getPostId, postId)
               .eq(TeaComment::getIsDeleted, 0)
               .isNull(TeaComment::getRootId) // 仅查一级评论
               .orderByAsc(TeaComment::getCreateTime);
        this.page(p, wrapper);

        List<TeaCommentVO> records = p.getRecords().stream().map(c -> {
            TeaCommentVO vo = toVO(c);
            // 查子评论
            List<TeaComment> replies = this.lambdaQuery()
                    .eq(TeaComment::getRootId, c.getId())
                    .eq(TeaComment::getIsDeleted, 0)
                    .orderByAsc(TeaComment::getCreateTime)
                    .list();
            vo.setChildren(replies.stream().map(this::toVO).collect(Collectors.toList()));
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(records, p.getTotal(), p.getCurrent(), p.getSize());
    }

    private TeaCommentVO toVO(TeaComment comment) {
        TeaCommentVO vo = new TeaCommentVO();
        BeanUtils.copyProperties(comment, vo);
        vo.setCreateTime(comment.getCreateTime() != null ? comment.getCreateTime().format(FORMATTER) : null);
        
        User u = userService.getById(comment.getUserId());
        if (u != null) {
            com.xytgy.teamallbackend.module.teacircle.vo.AuthorVO author = new com.xytgy.teamallbackend.module.teacircle.vo.AuthorVO();
            author.setId(u.getId());
            author.setNickname(u.getNickname() != null ? u.getNickname() : u.getUserAccount());
            author.setAvatar(u.getAvatar());
            vo.setAuthor(author);
        }

        // Set reply to user info if applicable
        if (comment.getReplyToUserId() != null) {
            User target = userService.getById(comment.getReplyToUserId());
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
