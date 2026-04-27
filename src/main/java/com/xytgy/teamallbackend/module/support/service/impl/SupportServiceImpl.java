package com.xytgy.teamallbackend.module.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.support.dto.SupportCreateRequest;
import com.xytgy.teamallbackend.module.support.entity.SupportTicket;
import com.xytgy.teamallbackend.module.support.repository.SupportTicketMapper;
import com.xytgy.teamallbackend.module.support.service.SupportService;
import com.xytgy.teamallbackend.module.support.vo.SupportTicketVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SupportServiceImpl extends ServiceImpl<SupportTicketMapper, SupportTicket> implements SupportService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void createSupport(Long userId, SupportCreateRequest request) {
        if (request == null) {
            throw new ServiceException(ResultCode.BAD_REQUEST, "参数错误");
        }
        SupportTicket ticket = new SupportTicket();
        ticket.setUserId(userId);
        ticket.setCategory(request.getCategory());
        ticket.setTitle(request.getTitle());
        ticket.setContent(request.getContent());
        ticket.setContact(request.getContact());
        ticket.setStatus("unread");
        save(ticket);
    }

    @Override
    public Map<String, Object> listSupports(Long userId, Integer page, Integer pageSize, String category) {
        Page<SupportTicket> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 10);
        QueryWrapper<SupportTicket> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        
        if (StringUtils.hasText(category)) {
            queryWrapper.eq("category", category);
        }
        queryWrapper.orderByDesc("create_time");

        Page<SupportTicket> ticketPage = this.page(pageParam, queryWrapper);

        List<SupportTicketVO> voList = ticketPage.getRecords().stream().map(t -> SupportTicketVO.builder()
                .id(t.getId())
                .category(t.getCategory())
                .title(t.getTitle())
                .content(t.getContent())
                .contact(t.getContact())
                .status(t.getStatus())
                .createTime(t.getCreateTime() != null ? t.getCreateTime().format(TIME_FORMATTER) : null)
                .reply(t.getReplyContent())
                .replyTime(t.getReplyTime() != null ? t.getReplyTime().format(TIME_FORMATTER) : null)
                .build()
        ).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("total", ticketPage.getTotal());
        result.put("page", pageParam.getCurrent());
        result.put("pageSize", pageParam.getSize());
        result.put("list", voList);
        return result;
    }

    @Override
    public Map<String, Integer> getSupportStats(Long userId) {
        QueryWrapper<SupportTicket> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("category", "COUNT(*) as count")
                .eq("user_id", userId)
                .groupBy("category");
                
        List<Map<String, Object>> resultMaps = this.listMaps(queryWrapper);
        
        Map<String, Integer> stats = new HashMap<>();
        stats.put("all", 0);
        stats.put("order", 0);
        stats.put("product", 0);
        stats.put("other", 0);
        
        int total = 0;
        for (Map<String, Object> map : resultMaps) {
            String category = (String) map.get("category");
            Integer count = ((Number) map.get("count")).intValue();
            total += count;
            
            if ("订单问题".equals(category)) {
                stats.put("order", count);
            } else if ("产品咨询".equals(category)) {
                stats.put("product", count);
            } else if ("其他问题".equals(category)) {
                stats.put("other", count);
            }
        }
        stats.put("all", total);
        
        return stats;
    }
}