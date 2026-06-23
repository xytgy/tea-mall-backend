package com.xytgy.teamallbackend.module.teacircle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.module.teacircle.entity.CommentLike;
import com.xytgy.teamallbackend.module.teacircle.mapper.CommentLikeMapper;
import com.xytgy.teamallbackend.module.teacircle.service.CommentLikeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class CommentLikeServiceImpl extends ServiceImpl<CommentLikeMapper, CommentLike> implements CommentLikeService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Boolean> toggleLike(Long userId, Long commentId) {
        LambdaQueryWrapper<CommentLike> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommentLike::getUserId, userId).eq(CommentLike::getCommentId, commentId);
        CommentLike existing = this.getOne(wrapper);

        boolean isLiked;
        if (existing != null) {
            this.removeById(existing.getId());
            isLiked = false;
        } else {
            CommentLike like = new CommentLike();
            like.setUserId(userId);
            like.setCommentId(commentId);
            this.save(like);
            isLiked = true;
        }

        Map<String, Boolean> result = new HashMap<>();
        result.put("isLiked", isLiked);
        return result;
    }

    @Override
    public boolean isLiked(Long userId, Long commentId) {
        if (userId == null || commentId == null) return false;
        return this.count(new LambdaQueryWrapper<CommentLike>()
                .eq(CommentLike::getUserId, userId)
                .eq(CommentLike::getCommentId, commentId)) > 0;
    }
}