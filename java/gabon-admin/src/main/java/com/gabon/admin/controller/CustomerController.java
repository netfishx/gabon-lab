package com.gabon.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import com.gabon.admin.model.dto.ChangeCustomerPasswordRequest;
import com.gabon.admin.model.dto.CustomerResponse;
import com.gabon.admin.service.CustomerService;

import com.gabon.common.util.JsonData;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户管理控制器
 * Customer Management Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "客户管理", description = "Customer Management APIs")
public class CustomerController {

    private final CustomerService customerService;

    @Operation(summary = "分页查询客户列表", description = "支持按客户名称、VIP状态进行分页查询", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "查询成功，返回客户分页数据"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效"),
            @ApiResponse(responseCode = "403", description = "无权限访问")
    })
    @GetMapping
    public JsonData<IPage<CustomerResponse>> getCustomers(
            @Parameter(description = "页码，从1开始 (可选，默认1) | Page number (optional, default 1)", example = "1") @RequestParam(name = "page", defaultValue = "1") int page,

            @Parameter(description = "每页大小 (可选，默认10) | Page size (optional, default 10)", example = "10") @RequestParam(name = "size", defaultValue = "10") int size,

            @Parameter(description = "客户名称，模糊查询 (可选) | Customer name, fuzzy search (optional)", example = "张三") @RequestParam(name = "name", required = false) String name,

            @Parameter(description = "VIP状态 (可选) | VIP Status (optional): 0=non-VIP, 1=VIP", example = "1") @RequestParam(name = "isVip", required = false) Integer isVip) {

        IPage<CustomerResponse> customers = customerService.findCustomers(page, size, name, isVip);
        return JsonData.buildSuccess(customers);
    }

    @Operation(summary = "修改客户密码", description = "管理员修改客户密码，无需验证旧密码", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "密码修改成功"),
            @ApiResponse(responseCode = "400", description = "请求参数无效"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效"),
            @ApiResponse(responseCode = "403", description = "无权限访问"),
            @ApiResponse(responseCode = "404", description = "客户不存在")
    })
    @PostMapping("/change-password")
    public JsonData<Void> changePassword(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "修改密码请求 | Change Password Request", required = true) @RequestBody ChangeCustomerPasswordRequest request) {

        if (request.getCustomerId() == null) {
            return JsonData.buildError("客户ID不能为空");
        }

        if (request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()) {
            return JsonData.buildError("新密码不能为空");
        }

        boolean success = customerService.changePassword(request.getCustomerId(), request.getNewPassword());

        if (success) {
            return JsonData.buildSuccess();
        } else {
            return JsonData.buildError("修改密码失败，客户可能不存在或已被删除");
        }
    }
}
