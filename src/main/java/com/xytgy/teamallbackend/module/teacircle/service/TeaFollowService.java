package com.xytgy.teamallbackend.module.teacircle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaFollow;
import com.xytgy.teamallbackend.module.teacircle.vo.SimpleUserVO;

import java.util.Map;

public interface TeaFollowService extends IService<TeaFollow> {
    Map<String, Boolean> toggleFollow(Long followerId, Long followingId);
    PageResult<SimpleUserVO> getFollowingList(Long currentUserId, int page, int pageSize);
    PageResult<SimpleUserVO> getFollowersList(Long currentUserId, int page, int pageSize);
    boolean isFollowing(Long followerId, Long followingId);
}
