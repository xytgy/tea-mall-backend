package com.xytgy.teamallbackend.module.product.mapper;

import com.xytgy.teamallbackend.module.product.entity.Product;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
* @author xytgy
* @description 针对表【product】的数据库操作Mapper
* @createDate 2026-04-14 20:05:50
* @Entity com.xytgy.teamallbackend.module.product.entity.Product
*/
public interface ProductMapper extends BaseMapper<Product> {

    @Select("""
            SELECT update_time
            FROM product
            WHERE id = #{productId}
              AND status = 1
              AND audit_status = 1
              AND is_deleted = 0
            LIMIT 1
            """)
    LocalDateTime selectActiveProductUpdateTime(@Param("productId") Long productId);

    @Select("""
            SELECT id
            FROM product
            WHERE id > #{lastId}
              AND is_deleted = 0
            ORDER BY id
            LIMIT #{limit}
            """)
    List<Long> selectIdsAfter(@Param("lastId") long lastId, @Param("limit") int limit);
}




