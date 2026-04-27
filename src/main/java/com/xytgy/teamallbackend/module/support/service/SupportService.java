package com.xytgy.teamallbackend.module.support.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.support.dto.SupportCreateRequest;
import com.xytgy.teamallbackend.module.support.entity.SupportTicket;
import com.xytgy.teamallbackend.module.support.vo.SupportTicketVO;

import java.util.Map;

public interface SupportService extends IService<SupportTicket> {
    void createSupport(Long userId, SupportCreateRequest request);
    Map<String, Object> listSupports(Long userId, Integer page, Integer pageSize, String category);
    Map<String, Integer> getSupportStats(Long userId);
}