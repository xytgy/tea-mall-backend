package com.xytgy.teamallbackend.module.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.feedback.dto.FeedbackSubmitRequest;
import com.xytgy.teamallbackend.module.feedback.entity.Feedback;
import com.xytgy.teamallbackend.module.feedback.mapper.FeedbackMapper;
import com.xytgy.teamallbackend.module.feedback.service.FeedbackService;
import com.xytgy.teamallbackend.module.feedback.vo.FeedbackVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, Feedback> implements FeedbackService {

    private final ObjectMapper objectMapper;

    @Override
    public void submitFeedback(Long userId, FeedbackSubmitRequest request) {
        if (request == null || request.getType() == null || request.getContent() == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "反馈类型和内容不能为空");
        }
        
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(request.getType());
        feedback.setContent(request.getContent());
        feedback.setContact(request.getContact());
        feedback.setStatus(0); // 未处理

        if (request.getImages() != null && !request.getImages().isEmpty()) {
            try {
                feedback.setImages(objectMapper.writeValueAsString(request.getImages()));
            } catch (JsonProcessingException e) {
                // Ignore or handle properly
                feedback.setImages("[]");
            }
        }

        this.save(feedback);
    }

    @Override
    public Map<String, Object> listFeedback(Integer page, Integer pageSize, Integer status, String type) {
        Page<Feedback> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        QueryWrapper<Feedback> queryWrapper = new QueryWrapper<>();
        
        if (status != null) {
            queryWrapper.eq("status", status);
        }
        if (StringUtils.hasText(type)) {
            queryWrapper.eq("type", type);
        }
        queryWrapper.orderByDesc("create_time");

        Page<Feedback> feedbackPage = this.page(pageParam, queryWrapper);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        List<FeedbackVO> voList = feedbackPage.getRecords().stream().map(f -> {
            List<String> imageList = new ArrayList<>();
            if (StringUtils.hasText(f.getImages()) && !f.getImages().equals("[]")) {
                try {
                    imageList = objectMapper.readValue(f.getImages(), new TypeReference<List<String>>() {});
                } catch (JsonProcessingException ignored) {
                    // intentionally empty
                }
            }
            
            return FeedbackVO.builder()
                    .id(f.getId())
                    .userId(f.getUserId())
                    .type(f.getType())
                    .content(f.getContent())
                    .images(imageList)
                    .contact(f.getContact())
                    .status(f.getStatus())
                    .createTime(f.getCreateTime() != null ? f.getCreateTime().format(formatter) : null)
                    .build();
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("total", feedbackPage.getTotal());
        result.put("list", voList);
        return result;
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (id == null || status == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数不完整");
        }
        Feedback feedback = this.getById(id);
        if (feedback == null) {
            throw new ServiceException(ResultCode.NOT_FOUND, "反馈记录不存在");
        }
        feedback.setStatus(status);
        this.updateById(feedback);
    }
}
