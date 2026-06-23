package com.xytgy.teamallbackend.mq.scheduler;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.xytgy.teamallbackend.mq.entity.MqRetryRecord;
import com.xytgy.teamallbackend.mq.mapper.MqRetryRecordMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.messaging.Message;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqRetrySchedulerTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                MqRetryRecord.class
        );
    }

    @Test
    void retriesClaimedRecordAndSendsMessage() {
        MqRetryRecordMapper mapper = mock(MqRetryRecordMapper.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        MqRetryScheduler scheduler = new MqRetryScheduler(mapper, rocketMQTemplate);
        MqRetryRecord record = buildRecord();

        when(mapper.selectList(any())).thenReturn(List.of(record));
        when(mapper.update(isNull(), any())).thenReturn(1, 1);

        scheduler.retryPendingMessages();

        verify(rocketMQTemplate).syncSend(anyString(), ArgumentMatchers.<Message<String>>any());
        verify(mapper).selectList(any());
        verify(mapper, times(2)).update(isNull(), any());
    }

    @Test
    void skipsRecordWhenClaimFails() {
        MqRetryRecordMapper mapper = mock(MqRetryRecordMapper.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        MqRetryScheduler scheduler = new MqRetryScheduler(mapper, rocketMQTemplate);
        MqRetryRecord record = buildRecord();

        when(mapper.selectList(any())).thenReturn(List.of(record));
        when(mapper.update(isNull(), any())).thenReturn(0);

        scheduler.retryPendingMessages();

        verify(rocketMQTemplate, never()).syncSend(anyString(), ArgumentMatchers.<Message<String>>any());
    }

    private MqRetryRecord buildRecord() {
        MqRetryRecord record = new MqRetryRecord();
        record.setId(1L);
        record.setTopic("TOPIC_FLASH_ORDER");
        record.setTags("FLASH_ORDER");
        record.setMessageKey("flash:1:2:3:123");
        record.setMessageBody("{\"transactionId\":\"flash:1:2:3:123\"}");
        record.setRetryCount(0);
        record.setMaxRetryCount(4);
        record.setStatus("PENDING");
        record.setNextRetryTime(LocalDateTime.now().minusSeconds(1));
        record.setCreateTime(LocalDateTime.now().minusMinutes(1));
        record.setUpdateTime(LocalDateTime.now().minusMinutes(1));
        return record;
    }
}
