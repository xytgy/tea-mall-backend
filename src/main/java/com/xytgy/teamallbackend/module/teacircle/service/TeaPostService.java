package com.xytgy.teamallbackend.module.teacircle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.module.teacircle.dto.TeaPostAddRequest;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaPost;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaPostVO;

import java.util.Map;

public interface TeaPostService extends IService<TeaPost> {
    TeaPostVO addPost(Long userId, TeaPostAddRequest request);
    PageResult<TeaPostVO> getExplorePosts(Long userId, int page, int pageSize);
    PageResult<TeaPostVO> getFollowingPosts(Long userId, int page, int pageSize);
    PageResult<TeaPostVO> getUserPosts(Long currentUserId, Long targetUserId, int page, int pageSize);
    PageResult<TeaPostVO> getTopicPosts(Long currentUserId, String topicName, int page, int pageSize);
    TeaPostVO getPostDetail(Long userId, Long postId);
    void deletePost(Long userId, Long postId);
    Map<String, Object> toggleLike(Long userId, Long postId);
}
