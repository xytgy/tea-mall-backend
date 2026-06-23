package com.xytgy.teamallbackend.module.banner.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.module.banner.entity.Banner;
import com.xytgy.teamallbackend.module.banner.mapper.BannerMapper;
import com.xytgy.teamallbackend.module.banner.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author xytgy
 * @description 针对表【banner】的数据库操作Service实现
 */
@Service
@RequiredArgsConstructor
public class BannerServiceImpl extends ServiceImpl<BannerMapper, Banner>
        implements BannerService {

    @Override
    public List<Banner> listActiveBanners() {
        return lambdaQuery()
                .eq(Banner::getStatus, 1)
                .orderByDesc(Banner::getSortOrder)
                .list();
    }
}
