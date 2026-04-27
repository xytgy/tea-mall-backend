package com.xytgy.teamallbackend.module.teacircle.controller;

import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.module.teacircle.service.TeaTopicService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaTopicVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "茶友圈 - 话题")
@RequestMapping("/api/tea-circle/topics")
public class TeaCircleTopicController {

    @Autowired
    private TeaTopicService teaTopicService;

    @GetMapping
    @Operation(summary = "获取推荐话题列表")
    public Result<PageResult<TeaTopicVO>> getTopics(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(teaTopicService.getTopics(page, pageSize));
    }
}
