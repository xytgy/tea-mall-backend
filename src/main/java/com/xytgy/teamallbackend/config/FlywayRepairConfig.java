package com.xytgy.teamallbackend.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * @deprecated 已合并到 FlywayRepairRunner，此类不再使用
 */
@Slf4j
@Deprecated
public class FlywayRepairConfig {

}
