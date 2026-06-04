package com.xytgy.teamallbackend.module.user.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户统计数据直查 Mapper，绕过 Service 层避免循环依赖。
 */
@Mapper
public interface UserStatsMapper {

    @Select("SELECT COUNT(*) FROM favorite WHERE user_id = #{userId}")
    long countFavorites(Long userId);

    @Select("SELECT COUNT(*) FROM orders WHERE user_id = #{userId} AND status != 4")
    long countOrders(Long userId);

    @Select("SELECT COUNT(*) FROM support_ticket WHERE user_id = #{userId}")
    long countSupportTickets(Long userId);
}
