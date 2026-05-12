package com.logistics.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.logistics.api.mapper")
public class LogisticsApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogisticsApiApplication.class, args);
    }
}
