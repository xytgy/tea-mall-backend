package com.xytgy.teamallbackend.module.teacircle.controller;

import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.security.SecurityUtils;
import com.xytgy.teamallbackend.module.teacircle.service.TeaPostService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaTopicService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaTopicVO;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaPostVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "茶友圈 - 话题")
@RequestMapping("/api/tea-circle/topics")
@RequiredArgsConstructor
public class TeaCircleTopicController {

    private final TeaTopicService teaTopicService;
    
    private final TeaPostService teaPostService;

    @GetMapping
    @Operation(summary = "获取推荐话题列表")
    public Result<PageResult<TeaTopicVO>> getTopics(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(teaTopicService.getTopics(page, pageSize));
    }
    
    @GetMapping("/name/{name}")
    @Operation(summary = "根据话题名称获取话题详情")
    public Result<TeaTopicVO> getTopicByName(@PathVariable String name) {
        TeaTopicVO topic = teaTopicService.getTopicByName(name, true);
        return Result.success(topic);
    }

    @GetMapping("/name/{name}/posts")
    @Operation(summary = "获取话题下的动态列表")
    public Result<PageResult<TeaPostVO>> getTopicPosts(@PathVariable String name,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
        Long currentUserId = null;
        try {
            currentUserId = SecurityUtils.getCurrentUserId();
        } catch (Exception e) {
            currentUserId = null;
        }
        return Result.success(teaPostService.getTopicPosts(currentUserId, name, page, pageSize));
    }
}
