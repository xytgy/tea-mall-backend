package com.xytgy.teamallbackend.module.teacircle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.module.teacircle.dto.TeaCommentAddRequest;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaComment;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaCommentVO;

public interface TeaCommentService extends IService<TeaComment> {
    TeaCommentVO addComment(Long userId, Long postId, TeaCommentAddRequest request);
    PageResult<TeaCommentVO> listComments(Long postId, int page, int pageSize);
}
