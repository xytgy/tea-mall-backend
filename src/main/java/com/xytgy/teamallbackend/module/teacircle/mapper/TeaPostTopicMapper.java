package com.xytgy.teamallbackend.module.teacircle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaPostTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TeaPostTopicMapper extends BaseMapper<TeaPostTopic> {
    @Select("SELECT COUNT(1) FROM tea_post p JOIN tea_post_topic pt ON pt.post_id = p.id WHERE pt.topic_id = #{topicId} AND p.is_deleted = 0")
    long countPostsByTopic(@Param("topicId") Long topicId);

    @Select("SELECT p.id FROM tea_post p JOIN tea_post_topic pt ON pt.post_id = p.id WHERE pt.topic_id = #{topicId} AND p.is_deleted = 0 ORDER BY p.create_time DESC LIMIT #{offset}, #{size}")
    List<Long> selectPostIdsByTopic(@Param("topicId") Long topicId, @Param("offset") long offset, @Param("size") long size);
}
