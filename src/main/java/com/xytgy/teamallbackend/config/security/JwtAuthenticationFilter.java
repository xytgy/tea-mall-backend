package com.xytgy.teamallbackend.config.security;

import com.xytgy.teamallbackend.common.UserRole;
import com.xytgy.teamallbackend.module.user.dto.UserInfoCache;
import com.xytgy.teamallbackend.module.user.entity.User;
import com.xytgy.teamallbackend.module.user.service.UserService;
import com.xytgy.teamallbackend.module.shop.service.ShopService;
import com.xytgy.teamallbackend.security.JwtAuthenticationToken;
import com.xytgy.teamallbackend.utils.JwtUtils;
import com.xytgy.teamallbackend.utils.RedisUtils