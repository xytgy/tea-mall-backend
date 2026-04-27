package com.xytgy.teamallbackend.module.feedback.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.feedback.dto.FeedbackSubmitRequest;
import com.xytgy.teamallbackend.module.feedback.entity.Feedback;
import com.xytgy.teamallbackend.module.feedback.repository.FeedbackMapper;
import com.xytgy.teamallbackend.module.feedback.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, Feedback> implements FeedbackService {

    @Autowired
    private ObjectMapper objectMapper;

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
}
