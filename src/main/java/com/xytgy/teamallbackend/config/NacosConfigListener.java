package com.xytgy.teamallbackend.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Nacos 配置动态刷新监听器。
 * <p>
 * 监听 Nacos 配置变更事件，当远端配置发生变化时：
 * <ol>
 *   <li>打印变更日志，便于运维排查</li>
 *   <li>发布 {@link EnvironmentChangeEvent}，触发所有
 *       {@code @RefreshScope} 和 {@code @ConfigurationProperties} Bean 重新绑定</li>
 * </ol>
 * <p>
 * 使用方式：在需要动态刷新的 Bean 上添加 {@code @RefreshScope} 注解即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(NacosConfigManager.class)
public class NacosConfigListener {

    private final NacosConfigManager nacosConfigManager;
    private final ApplicationContext applicationContext;

    @Value("${spring.cloud.nacos.config.group:DEFAULT_GROUP}")
    private String group;

    @Value("${spring.cloud.nacos.config.file-extension:yaml}")
    private String fileExtension;

    /**
     * Nacos 监听器使用的线程池，避免阻塞 Nacos 客户端 IO 线程。
     */
    private final Executor listenerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nacos-config-listener");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void registerListener() {
        String dataId = "tea-mall-backend" + "." + fileExtension;
        try {
            nacosConfigManager.getConfigService().addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return listenerExecutor;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("[Nacos] 配置变更检测 -> dataId={}, group={}, 内容长度={} 字符",
                            dataId, group, configInfo == null ? 0 : configInfo.length());
                    // 发布 EnvironmentChangeEvent 触发 @ConfigurationProperties 重新绑定
                    applicationContext.publishEvent(
                            new EnvironmentChangeEvent(applicationContext, null));
                    log.info("[Nacos] 已发布 EnvironmentChangeEvent，@RefreshScope Bean 将被刷新");
                }
            });
            log.info("[Nacos] 配置监听器注册成功 -> dataId={}, group={}", dataId, group);
        } catch (Exception e) {
            log.warn("[Nacos] 配置监听器注册失败（Nacos 不可用时不影响本地配置启动）-> dataId={}, group={}, 原因: {}",
                    dataId, group, e.getMessage());
        }
    }
}
