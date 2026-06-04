package com.xytgy.teamallbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Slf4j
@Configuration
@Profile("dev")
public class FlywayRepairRunner {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            log.info("执行 Flyway repair，清理失败的迁移记录...");
            flyway.repair();
            log.info("Flyway repair 完成，开始执行迁移...");
            flyway.migrate();
        };
    }
}
