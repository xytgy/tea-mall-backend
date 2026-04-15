package com.xytgy.teamallbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.entity.User;
import com.xytgy.teamallbackend.service.UserService;
import com.xytgy.teamallbackend.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
* @author xytgy
* @description 针对表【user】的数据库操作Service实现
* @createDate 2026-04-15 07:59:22
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

}




