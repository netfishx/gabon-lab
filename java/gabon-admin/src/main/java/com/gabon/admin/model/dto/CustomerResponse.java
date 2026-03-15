package com.gabon.admin.model.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.gabon.admin.config.InstantToSecondsSerializer;
import com.gabon.admin.model.entity.Customer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 客户响应DTO
 * Customer Response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "客户响应")
public class CustomerResponse {

    @Schema(description = "客户ID", example = "1")
    private Long id;

    @Schema(description = "账户名", example = "zhangsan")
    private String username;

    @Schema(description = "客户名称", example = "张三")
    private String name;

    @Schema(description = "电话", example = "13900139001")
    private String phone;

    @Schema(description = "VIP状态: 0=普通用户, 1=VIP", example = "1")
    private Integer isVip;

    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Schema(description = "注册时间(时间戳-秒)", example = "1769749549")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant registrationTime;

    @Schema(description = "最后登录时间(时间戳-秒)", example = "1770255652")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant lastLoginAt;

    @Schema(description = "创建时间(时间戳-秒)", example = "1769749549")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant createTime;

    @Schema(description = "更新时间(时间戳-秒)", example = "1770275667")
    @JsonSerialize(using = InstantToSecondsSerializer.class)
    private Instant updateTime;

    /**
     * 从实体转换为响应DTO
     */
    public static CustomerResponse fromEntity(Customer customer) {
        if (customer == null) {
            return null;
        }
        return CustomerResponse.builder()
                .id(customer.getId())
                .username(customer.getUsername())
                .name(customer.getName())
                .phone(customer.getPhone())
                .isVip(customer.getIsVip())
                .avatarUrl(customer.getAvatarUrl())
                .registrationTime(customer.getRegistrationTime())
                .lastLoginAt(customer.getLastLoginAt())
                .createTime(customer.getCreateTime())
                .updateTime(customer.getUpdateTime())
                .build();
    }
}
