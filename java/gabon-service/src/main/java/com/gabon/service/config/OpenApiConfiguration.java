package com.gabon.service.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger 配置类
 * 提供API文档生成和在线测试功能
 */
@Configuration
public class OpenApiConfiguration {

        /**
         * 全局OpenAPI配置
         */
        @Bean
        public OpenAPI customOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("gabon Service API")
                                                .version("1.0.0")
                                                .description("客户前台服务，提供视频上传、管理等功能接口\n\n"
                                                                + "## 常用枚举值 | Common Enum Values\n"
                                                                + "- **前端视频状态 VideoStatus (前端/Client)**: 用于 GET /videos/my 的 status 参数\n"
                                                                + "  - `3` = 审核中 (Under Review) → 后端实际查询 status IN (1, 2, 3)\n"
                                                                + "  - `4` = 已上架 (Published) → 后端实际查询 status = 4\n"
                                                                + "  - `5` = 未通过 (Not Passed) → 后端实际查询 status IN (0, 5)\n"
                                                                + "  - 不传 status → 不过滤，返回全部"))
                                .components(new Components()
                                                .addSecuritySchemes("Bearer",
                                                                new SecurityScheme()
                                                                                .type(SecurityScheme.Type.HTTP)
                                                                                .scheme("bearer")
                                                                                .bearerFormat("JWT")
                                                                                .description("JWT认证令牌，格式：Bearer {token}")));
        }
}
