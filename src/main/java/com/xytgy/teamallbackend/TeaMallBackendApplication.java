package com.xytgy.teamallbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan({"com.xytgy.teamallbackend.module.*.mapper", "com.xytgy.teamallbackend.mq.mapper"})
@EnableAsync
@EnableScheduling
public class TeaMallBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeaMallBackendApplication.class, args);
    }

}
