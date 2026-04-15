package com.xytgy.teamallbackend.controller;



import com.xytgy.teamallbackend.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Slf4j
public class UserController {


    @PostMapping("/login")
    public String login(@RequestBody User user) {
        log.info("登录接口调试成功");


        return "success";
    }
}
