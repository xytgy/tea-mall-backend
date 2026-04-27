package com.xytgy.teamallbackend.module.teacircle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaTopic;
import com.xytgy.teamallbackend.module.teacircle.repository.TeaTopicMapper;
import com.xytgy.teamallbackend.module.teacircle.service.TeaTopicService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaTopicVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeaTopicServiceImpl extends ServiceImpl<TeaTopicMapper, TeaTopic> implements TeaTopicService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public PageResult<TeaTopicVO> getTopics(int page, int pageSize) {
        Page<TeaTopic> p = new Page<>(page, pageSize);
        this.page(p, new LambdaQueryWrapper<TeaTopic>()
                .orderByDesc(TeaTopic::getIsHot)
                .orderByDesc(TeaTopic::getCreateTime));

        List<TeaTopicVO> list = p.getRecords().stream().map(t -> {
            TeaTopicVO vo = new TeaTopicVO();
            BeanUtils.copyProperties(t, vo);
            vo.setId(String.valueOf(t.getId()));
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(list, p.getTotal(), p.getCurrent(), p.getSize());
    }
}
