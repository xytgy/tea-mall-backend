package com.xytgy.teamallbackend.module.banner.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xytgy.teamallbackend.module.banner.entity.Banner;

import java.util.List;

/**
 * @author xytgy
 * @description 针对表【banner】的数据库操作Service
 */
public interface BannerService extends IService<Banner> {

    List<Banner> listActiveBanners();
}
