package com.gabon.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

/**
 * Jackson全局配置
 * Global Jackson Configuration
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        SimpleModule module = new SimpleModule();
        // Register custom serializer for Instant
        module.addSerializer(Instant.class, new InstantToSecondsSerializer());
        objectMapper.registerModule(module);

        return objectMapper;
    }
}
