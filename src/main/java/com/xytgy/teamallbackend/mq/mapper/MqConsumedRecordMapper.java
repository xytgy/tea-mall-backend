package com.xytgy.teamallbackend.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xytgy.teamallbackend.mq.entity.MqConsumedRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息消费记录 Mapper
 */
@Mapper
public interface MqConsumedRecordMapper extends BaseMapper<MqConsumedRecord> {

}
