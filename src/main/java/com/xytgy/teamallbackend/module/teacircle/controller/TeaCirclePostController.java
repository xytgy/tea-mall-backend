package com.xytgy.teamallbackend.module.teacircle.controller;

import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.teacircle.dto.TeaCommentAddRequest;
import com.xytgy.teamallbackend.module.teacircle.dto.TeaPostAddRequest;
import com.xytgy.teamallbackend.module.teacircle.service.TeaCommentService;
import com.xytgy.teamallbackend.module.teacircle.service.TeaPostService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaCommentVO;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaPostVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "茶友圈 - 动态与评论")
@RequestMapping("/api/tea-circle/posts")
@RequiredArgsConstructor
public class TeaCirclePostController {

    private final TeaPostService teaPostService;
    private final TeaCommentService teaCommentService;

    @PostMapping
    @Operation(summary = "发布动态")
    public Result<TeaPostVO> addPost(@RequestBody TeaPostAddRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(teaPostService.addPost(userId, request));
    }

    @GetMapping("/explore")
    @Operation(summary = "获取最新动态流(广场)")
    public Result<PageResult<TeaPostVO>> explore(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = UserContext.getCurrentUserId(); // 可选登录
        return Result.success(teaPostService.getExplorePosts(userId, page, pageSize));
    }

    @GetMapping("/following")
    @Operation(summary = "获取关注者的动态流(朋友圈)")
    public Result<PageResult<TeaPostVO>> following(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(teaPostService.getFollowingPosts(userId, page, pageSize));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "获取指定用户的动态列表")
    public Result<PageResult<TeaPostVO>> userPosts(@PathVariable Long userId,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int pageSize) {
        Long currentUserId = UserContext.getCurrentUserId();
        return Result.success(teaPostService.getUserPosts(currentUserId, userId, page, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取动态详情")
    public Result<TeaPostVO> detail(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(teaPostService.getPostDetail(userId, id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除动态")
    public Result<Void> deletePost(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        teaPostService.deletePost(userId, id);
        return Result.success(null);
    }

    @PostMapping("/{id}/like")
    @Operation(summary = "点赞/取消点赞")
    public Result<Map<String, Object>> toggleLike(@PathVariable Long id) {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(teaPostService.toggleLike(userId, id));
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "发表评论")
    public Result<TeaCommentVO> addComment(@PathVariable Long id, @RequestBody TeaCommentAddRequest request) {
        Long userId = UserContext.getCurrentUserId();
        return Result.success(teaCommentService.addComment(userId, id, request));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "获取动态的评论列表")
    public Result<PageResult<TeaCommentVO>> getComments(@PathVariable Long id,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(teaCommentService.listComments(id, page, pageSize));
    }
}
