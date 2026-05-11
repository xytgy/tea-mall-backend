package com.xytgy.teamallbackend.module.user.service.impl;

import com.xytgy.teamallbackend.module.favorite.service.FavoriteService;
import com.xytgy.teamallbackend.module.order.service.OrdersService;
import com.xytgy.teamallbackend.module.support.service.SupportService;
import lombok.Getter;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Getter
public class UserLazyDeps {
    private final FavoriteService favoriteService;
    private final OrdersService ordersService;
    private final SupportService supportService;

    public UserLazyDeps(
            @Lazy FavoriteService favoriteService,
            @Lazy OrdersService ordersService,
            @Lazy SupportService supportService
    ) {
        this.favoriteService = favoriteService;
        this.ordersService = ordersService;
        this.supportService = supportService;
    }
}

