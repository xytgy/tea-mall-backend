package com.xytgy.teamallbackend.module.teacircle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.teacircle.entity.CommentLike;

import java.util.Map;

public interface CommentLikeService extends IService<CommentLike> {
    Map<String, Boolean> toggleLike(Long userId, Long commentId);
    boolean isLiked(Long userId, Long commentId);
}