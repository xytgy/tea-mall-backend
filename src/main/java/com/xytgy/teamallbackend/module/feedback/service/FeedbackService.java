package com.xytgy.teamallbackend.module.feedback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.feedback.dto.FeedbackSubmitRequest;
import com.xytgy.teamallbackend.module.feedback.entity.Feedback;
import com.xytgy.teamallbackend.module.feedback.vo.FeedbackVO;

import java.util.Map;

public interface FeedbackService extends IService<Feedback> {
    void submitFeedback(Long userId, FeedbackSubmitRequest request);
    Map<String, Object> listFeedback(Integer page, Integer pageSize, Integer status, String type);
    void updateStatus(Long id, Integer status);
}
