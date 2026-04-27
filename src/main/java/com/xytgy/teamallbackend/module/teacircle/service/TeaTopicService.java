package com.xytgy.teamallbackend.module.teacircle.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaTopic;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaTopicVO;

public interface TeaTopicService extends IService<TeaTopic> {
    PageResult<TeaTopicVO> getTopics(int page, int pageSize);
}
