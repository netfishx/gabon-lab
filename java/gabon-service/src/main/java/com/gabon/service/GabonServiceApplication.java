package com.gabon.service;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Gabon服务应用
 * 客户前台服务，提供视频上传、管理等功能
 */
@SpringBootApplication(scanBasePackages = { "com.gabon.service", "com.gabon.common" })
@MapperScan("com.gabon.service.mapper")
@EnableScheduling
public class GabonServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GabonServiceApplication.class, args);
    }

}
