package com.xytgy.teamallbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.xytgy.teamallbackend.module.*.repository")
public class TeaMallBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeaMallBackendApplication.class, args);
    }

}
