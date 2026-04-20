package com.xytgy.teamallbackend.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.dto.AdminUserAddRequest;
import com.xytgy.teamallbackend.dto.UserStatusRequest;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.service.UserService;
import com.xytgy.teamallbackend.vo.IdVO;
import com.xytgy.teamallbackend.vo.UserVO;
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
@RequestMapping("/api/user/admin")
public class AdminUserController {

    @Autowired
    private UserService userService;

    @PostMapping("/add")
    public Result<Void> add(@RequestBody AdminUserAddRequest request) {
        requireRole(1);
        userService.addUserByAdmin(request);
        return Result.success(null);
    }

    @GetMapping("/list")
    public Result<List<UserVO>> list() {
        requireRole(1);
        return Result.success(userService.listUsersByAdmin());
    }

    @PutMapping("/status")
    public Result<Void> updateStatus(@RequestBody UserStatusRequest request) {
        requireRole(1);
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
            throw new ServiceException(403, "无权限访问");
        }
    }
}
