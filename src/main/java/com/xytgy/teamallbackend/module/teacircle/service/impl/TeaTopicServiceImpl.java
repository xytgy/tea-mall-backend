package com.xytgy.teamallbackend.module.teacircle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaTopic;
import com.xytgy.teamallbackend.module.teacircle.repository.TeaTopicMapper;
import com.xytgy.teamallbackend.module.teacircle.service.TeaTopicService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaTopicVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeaTopicServiceImpl extends ServiceImpl<TeaTopicMapper, TeaTopic> implements TeaTopicService {

    @Override
    public PageResult<TeaTopicVO> getTopics(int page, int pageSize) {
        Page<TeaTopic> p = new Page<>(page, pageSize);
        this.page(p, new LambdaQueryWrapper<TeaTopic>()
                .orderByDesc(TeaTopic::getIsHot)
                .orderByDesc(TeaTopic::getPostCount)
                .orderByDesc(TeaTopic::getViewCount)
                .orderByDesc(TeaTopic::getId));

        List<TeaTopicVO> list = p.getRecords().stream().map(t -> TeaTopicVO.builder()
                .id(String.valueOf(t.getId()))
                .title(t.getTitle())
                .description(t.getDescription())
                .viewCount(t.getViewCount() == null ? "0" : String.valueOf(t.getViewCount()))
                .postCount(t.getPostCount())
                .isHot(t.getIsHot() != null && t.getIsHot() == 1)
                .build()
        ).collect(Collectors.toList());

        return new PageResult<>(list, p.getTotal(), p.getCurrent(), p.getSize());
    }

    @Override
    public TeaTopicVO getTopicByName(String name, boolean increaseView) {
        TeaTopic topic = this.getOne(new LambdaQueryWrapper<TeaTopic>().eq(TeaTopic::getName, name));
        if (topic == null) {
            return null;
        }
        if (increaseView) {
            TeaTopic update = new TeaTopic();
            update.setId(topic.getId());
            update.setViewCount((topic.getViewCount() == null ? 0L : topic.getViewCount()) + 1);
            this.updateById(update);
            topic.setViewCount(update.getViewCount());
        }
        return TeaTopicVO.builder()
                .id(String.valueOf(topic.getId()))
                .title(topic.getTitle())
                .description(topic.getDescription())
                .viewCount(topic.getViewCount() == null ? "0" : String.valueOf(topic.getViewCount()))
                .postCount(topic.getPostCount())
                .isHot(topic.getIsHot() != null && topic.getIsHot() == 1)
                .build();
    }

    @Override
    public TeaTopic getOrCreateTopicByName(String name, String title) {
        TeaTopic existing = this.getOne(new LambdaQueryWrapper<TeaTopic>().eq(TeaTopic::getName, name));
        if (existing != null) {
            return existing;
        }
        TeaTopic topic = new TeaTopic();
        topic.setName(name);
        topic.setTitle(title);
        topic.setIsHot(0);
        topic.setViewCount(0L);
        topic.setPostCount(0L);
        this.save(topic);
        return topic;
    }
}
