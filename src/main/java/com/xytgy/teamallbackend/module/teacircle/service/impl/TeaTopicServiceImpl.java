package com.xytgy.teamallbackend.module.teacircle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xytgy.teamallbackend.common.PageResult;
import com.xytgy.teamallbackend.module.teacircle.entity.TeaTopic;
import com.xytgy.teamallbackend.module.teacircle.mapper.TeaTopicMapper;
import com.xytgy.teamallbackend.module.teacircle.service.TeaTopicService;
import com.xytgy.teamallbackend.module.teacircle.vo.TeaTopicVO;
import com.xytgy.teamallbackend.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeaTopicServiceImpl extends ServiceImpl<TeaTopicMapper, TeaTopic> implements TeaTopicService {

    private final RedisUtils redisUtils;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String TOPIC_LIST_CACHE_KEY = "cache:topic:list:";
    private static final String TOPIC_VIEW_KEY = "cache:topic:view:";
    private static final String TOPIC_VIEW_DB_KEY = "db:topic:view:";

    @Override
    @SuppressWarnings("unchecked")
    public PageResult<TeaTopicVO> getTopics(int page, int pageSize) {
        String cacheKey = TOPIC_LIST_CACHE_KEY + page + ":" + pageSize;
        return redisUtils.getOrLoad(cacheKey, PageResult.class, 15, () -> {
            Page<TeaTopic> p = new Page<>(page, pageSize);
            this.page(p, new LambdaQueryWrapper<TeaTopic>()
                    .orderByDesc(TeaTopic::getIsHot)
                    .orderByDesc(TeaTopic::getPostCount)
                    .orderByDesc(TeaTopic::getViewCount)
                    .orderByDesc(TeaTopic::getId));

            List<TeaTopicVO> list = p.getRecords().stream().map(t -> TeaTopicVO.builder()
                    .id(String.valueOf(t.getId()))
                    .title(t.getTitle())
                    .description(t.getDescription())
                    .viewCount(t.getViewCount() == null ? "0" : String.valueOf(t.getViewCount()))
                    .postCount(t.getPostCount())
                    .isHot(t.getIsHot() != null && t.getIsHot() == 1)
                    .build()
            ).toList();

            return new PageResult<>(list, p.getTotal(), p.getCurrent(), p.getSize());
        });
    }

    @Override
    public TeaTopicVO getTopicByName(String name, boolean increaseView) {
        TeaTopic topic = this.getOne(new LambdaQueryWrapper<TeaTopic>().eq(TeaTopic::getName, name));
        if (topic == null) {
            return null;
        }
        if (increaseView) {
            String viewKey = TOPIC_VIEW_KEY + topic.getId();
            stringRedisTemplate.opsForValue().increment(viewKey);
            String dbKey = TOPIC_VIEW_DB_KEY + topic.getId();
            long redisCount = Long.parseLong(stringRedisTemplate.opsForValue().get(viewKey));
            String syncedStr = stringRedisTemplate.opsForValue().get(dbKey);
            long synced = syncedStr != null ? Long.parseLong(syncedStr) : 0;
            // 每累积 10 次浏览量同步一次数据库，避免频繁写库
            if (redisCount - synced >= 10) {
                topic.setViewCount((topic.getViewCount() == null ? 0L : topic.getViewCount()) + (redisCount - synced));
                this.updateById(topic);
                stringRedisTemplate.opsForValue().set(dbKey, String.valueOf(redisCount));
            }
            topic.setViewCount((topic.getViewCount() == null ? 0L : topic.getViewCount()) + (redisCount - synced));
        }
        return TeaTopicVO.builder()
                .id(String.valueOf(topic.getId()))
                .title(topic.getTitle())
                .description(topic.getDescription())
                .viewCount(topic.getViewCount() == null ? "0" : String.valueOf(topic.getViewCount()))
                .postCount(topic.getPostCount())
                .isHot(topic.getIsHot() != null && topic.getIsHot() == 1)
                .build();
    }

    @Override
    public TeaTopic getOrCreateTopicByName(String name, String title) {
        TeaTopic existing = this.getOne(new LambdaQueryWrapper<TeaTopic>().eq(TeaTopic::getName, name));
        if (existing != null) {
            return existing;
        }
        TeaTopic topic = new TeaTopic();
        topic.setName(name);
        topic.setTitle(title);
        topic.setIsHot(0);
        topic.setViewCount(0L);
        topic.setPostCount(0L);
        this.save(topic);
        redisUtils.deleteByPattern(TOPIC_LIST_CACHE_KEY + "*");
        return topic;
    }
}
