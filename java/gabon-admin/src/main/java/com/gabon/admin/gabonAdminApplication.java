package com.gabon.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@MapperScan("com.gabon.admin.mapper")
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.gabon.admin", "com.gabon.common"})
public class gabonAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(gabonAdminApplication.class, args);
    }

}
