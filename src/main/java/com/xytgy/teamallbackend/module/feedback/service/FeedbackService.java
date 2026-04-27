package com.xytgy.teamallbackend.module.feedback.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.feedback.dto.FeedbackSubmitRequest;
import com.xytgy.teamallbackend.module.feedback.entity.Feedback;

public interface FeedbackService extends IService<Feedback> {
    void submitFeedback(Long userId, FeedbackSubmitRequest request);
}
