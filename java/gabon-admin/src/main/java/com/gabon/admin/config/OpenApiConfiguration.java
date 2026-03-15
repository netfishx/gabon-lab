package com.gabon.admin.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI/Swagger 配置类
 * 提供API文档生成和在线测试功能，包含枚举值文档
 */
@Configuration
public class OpenApiConfiguration {

        /**
         * 全局OpenAPI配置，包含枚举Schema定义
         */

        static {
                // 全局配置：将Instant类型映射为Long类型(时间戳)
                org.springdoc.core.utils.SpringDocUtils.getConfig().replaceWithClass(java.time.Instant.class,
                                Long.class);
        }

        /**
         * 全局OpenAPI配置，包含枚举Schema定义
         */
        @Bean
        public OpenAPI customOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("gabon Admin API")
                                                .description("gabon 管理后台 REST API 文档 | gabon Admin Backend REST API Documentation\n\n"
                                                                +
                                                                "## 常用枚举值 | Common Enum Values\n" +
                                                                "- **账户角色 UserRole**: 1=管理员(Admin), 2=普通用户(Normal)\n" +
                                                                "- **账户状态 UserStatus**: 0=禁用(Disabled), 1=启用(Enabled)\n"
                                                                +
                                                                "- **视频内部状态 VideoStatus (内部/Internal)**: 0=失败(Failed), 1=等待转码(Pending Transcode), 2=转码中(Transcoding), 3=等待审核(Pending Review), 4=审核通过(Approved), 5=审核不通过(Rejected)\n"
                                                                +
                                                                "  > GET /videos 默认不传 status 或传 -1 时，仅返回 3/4/5（排除转码流水线 0/1/2）；传具体值则精确过滤\n"
                                                                +
                                                                "- **VIP状态 VipStatus**: 0=普通用户(Non-VIP), 1=VIP会员(VIP)")
                                                .version("v1.0.0")
                                                .contact(new Contact()
                                                                .name("gabon Team")
                                                                .email("support@gabon.com")
                                                                .url("https://gabon.com"))
                                                .license(new License()
                                                                .name("Apache 2.0")
                                                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                                .components(new Components()
                                                .addSecuritySchemes("Bearer", new SecurityScheme()
                                                                .type(SecurityScheme.Type.HTTP)
                                                                .scheme("bearer")
                                                                .bearerFormat("JWT")
                                                                .description("JWT认证令牌，格式：Bearer {token}"))
                                                // 添加枚举Schema定义
                                                .addSchemas("UserRole", new StringSchema()
                                                                .description("账户角色枚举 | User Role Enum")
                                                                ._enum(java.util.Arrays.asList("1", "2"))
                                                                .example("1"))
                                                .addSchemas("UserStatus", new StringSchema()
                                                                .description("账户状态枚举 | User Status Enum")
                                                                ._enum(java.util.Arrays.asList("0", "1"))
                                                                .example("1")))
                                .addSecurityItem(new SecurityRequirement().addList("Bearer"));
        }

        /**
         * 认证相关API分组
         */
        @Bean
        public GroupedOpenApi authApi() {
                return GroupedOpenApi.builder()
                                .group("auth-management")
                                .displayName("认证管理")
                                .pathsToMatch("/api/auth/**")
                                .build();
        }

        /**
         * 账户管理API分组
         */
        @Bean
        public GroupedOpenApi userApi() {
                return GroupedOpenApi.builder()
                                .group("user-management")
                                .displayName("账户管理")
                                .pathsToMatch("/api/users/**")
                                .build();
        }

        /**
         * 客户管理API分组
         */
        @Bean
        public GroupedOpenApi customerApi() {
                return GroupedOpenApi.builder()
                                .group("customer-management")
                                .displayName("客户管理")
                                .pathsToMatch("/api/customers/**")
                                .build();
        }

        /**
         * 视频管理API分组
         */
        @Bean
        public GroupedOpenApi videoApi() {
                return GroupedOpenApi.builder()
                                .group("video-management")
                                .displayName("视频管理")
                                .pathsToMatch("/api/videos/**")
                                .build();
        }

        /**
         * 报表管理API分组
         */
        @Bean
        public GroupedOpenApi reportApi() {
                return GroupedOpenApi.builder()
                                .group("report-management")
                                .displayName("报表管理")
                                .pathsToMatch("/api/report/**")
                                .build();
        }

        /**
         * 所有API分组
         */
        @Bean
        public GroupedOpenApi allApi() {
                return GroupedOpenApi.builder()
                                .group("all-apis")
                                .displayName("全部接口")
                                .pathsToMatch("/api/**")
                                .build();
        }
}
