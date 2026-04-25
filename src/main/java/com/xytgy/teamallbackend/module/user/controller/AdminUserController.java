package com.xytgy.teamallbackend.module.user.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.ResultCode;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.module.user.dto.AdminUserAddRequest;
import com.xytgy.teamallbackend.module.user.dto.UserStatusRequest;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.module.product.vo.IdVO;
import com.xytgy.teamallbackend.module.user.vo.UserVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "管理员")
@RequestMapping("/api/user/admin")
public class AdminUserController {

    @Autowired
    private UserService userService;

    @PostMapping("/add")
    public Result<Void> add(@RequestBody AdminUserAddRequest request) {
        requireRole(2);
        userService.addUserByAdmin(request);
        return Result.success(null);
    }

    @GetMapping("/list")
    public Result<List<UserVO>> list() {
        requireRole(2);
        return Result.success(userService.listUsersByAdmin());
    }

    @PutMapping("/status")
    public Result<Void> updateStatus(@RequestBody UserStatusRequest request) {
        requireRole(2);
        userService.updateUserStatusByAdmin(request.getId(), request.getStatus());
        return Result.success(null);
    }

    private void requireRole(Integer expectRole) {
        Map<String, Object> user = UserContext.getUser();
        Integer role = null;
        if (user != null && user.get("role") != null) {
            role = Integer.valueOf(String.valueOf(user.get("role")));
        }
        if (!expectRole.equals(role)) {
            throw new ServiceException(ResultCode.FORBIDDEN, "无权限访问");
        }
    }
}
