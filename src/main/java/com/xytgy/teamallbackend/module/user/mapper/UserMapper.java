package com.xytgy.teamallbackend.module.user.mapper;

import com.xytgy.teamallbackend.module.user.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author xytgy
* @description 针对表【user】的数据库操作Mapper
* @createDate 2026-04-15 07:59:22
* @Entity com.xytgy.teamallbackend.module.user.entity.User
*/
public interface UserMapper extends BaseMapper<User> {

    @Select("""
            SELECT id
            FROM `user`
            WHERE id > #{lastId}
              AND is_deleted = 0
            ORDER BY id
            LIMIT #{limit}
            """)
    List<Long> selectIdsAfter(@Param("lastId") long lastId, @Param("limit") int limit);
}




