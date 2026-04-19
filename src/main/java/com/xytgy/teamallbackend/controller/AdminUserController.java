package com.xytgy.teamallbackend.controller;

import com.xytgy.teamallbackend.common.Result;
import com.xytgy.teamallbackend.common.UserContext;
import com.xytgy.teamallbackend.dto.AdminUserAddRequest;
import com.xytgy.teamallbackend.exception.ServiceException;
import com.xytgy.teamallbackend.service.UserService;
import com.xytgy.teamallbackend.vo.IdVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/user")
public class    AdminUserController {

    @Autowired
    private UserService userService;

    @PostMapping("/add")
    public Result<IdVO> add(@RequestBody AdminUserAddRequest request) {
        requireRole("admin");
        Long id = userService.addUserByAdmin(request);
        return Result.success("新增用户成功", new IdVO(id));
    }

    private void requireRole(String expectRole) {
        Map<String, Object> user = UserContext.getUser();
        String role = user == null ? null : String.valueOf(user.get("role"));
        if (!expectRole.equals(role)) {
            throw new ServiceException(403, "无权限访问");
        }
    }
}
