package com.gabon.admin.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.admin.annotation.RequireRole;
import com.gabon.admin.model.dto.AdminUserResponse;
import com.gabon.admin.model.dto.ChangePasswordRequest;
import com.gabon.admin.model.dto.CreateAdminUserRequest;
import com.gabon.admin.model.dto.UpdateAdminUserRequest;
import com.gabon.admin.model.entity.AdminUser;
import com.gabon.admin.service.AdminUserService;
import com.gabon.admin.service.AuthService;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.enums.UserRole;
import com.gabon.common.exception.BizException;
import com.gabon.common.util.JsonData;
import com.gabon.common.util.SecurityUtil;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理员用户管理控制器
 * Admin User Management Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/admin-users")
@RequiredArgsConstructor
@Tag(name = "管理员用户管理", description = "管理员用户的增删改查、密码管理等操作（仅管理员可访问）")
public class AdminUserController {

        private final AdminUserService adminUserService;
        private final AuthService authService;

        /**
         * 创建管理员用户
         */
        @RequireRole(UserRole.ADMIN) // 仅管理员可访问
        @Operation(summary = "创建管理员用户", description = "创建新的管理员用户账户（仅管理员可操作）", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "创建成功"),
                        @ApiResponse(responseCode = "400", description = "请求参数错误"),
                        @ApiResponse(responseCode = "401", description = "未认证"),
                        @ApiResponse(responseCode = "403", description = "权限不足"),
                        @ApiResponse(responseCode = "409", description = "用户名已存在")
        })
        @PostMapping
        public JsonData<AdminUserResponse> createAdminUser(
                        @Parameter(description = "创建管理员用户请求 (必填)", required = true) @Valid @RequestBody CreateAdminUserRequest request,
                        HttpServletRequest httpRequest) {

                String currentUsername = SecurityUtil.getCurrentUsername(httpRequest, token -> {
                        AdminUser user = authService.getCurrentUser(token);
                        return user != null ? user.getUsername() : null;
                });
                AdminUser user = adminUserService.createAdminUser(request, currentUsername);
                AdminUserResponse response = AdminUserResponse.fromEntity(user);

                return JsonData.buildSuccess(response);
        }

        /**
         * 更新管理员用户
         */
        @RequireRole(UserRole.ADMIN) // 仅管理员可访问
        @Operation(summary = "更新管理员用户", description = "更新管理员用户信息（仅管理员可操作，不包括密码）", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "更新成功"),
                        @ApiResponse(responseCode = "400", description = "请求参数错误"),
                        @ApiResponse(responseCode = "401", description = "未认证"),
                        @ApiResponse(responseCode = "403", description = "权限不足"),
                        @ApiResponse(responseCode = "404", description = "用户不存在")
        })
        @PutMapping("/{id}")
        public JsonData<AdminUserResponse> updateAdminUser(
                        @Parameter(description = "用户ID (必填)", required = true, example = "1") @PathVariable Long id,
                        @Parameter(description = "更新管理员用户请求 (必填)", required = true) @Valid @RequestBody UpdateAdminUserRequest request,
                        HttpServletRequest httpRequest) {

                String currentUsername = SecurityUtil.getCurrentUsername(httpRequest, token -> {
                        AdminUser user = authService.getCurrentUser(token);
                        return user != null ? user.getUsername() : null;
                });
                AdminUser user = adminUserService.updateAdminUser(id, request, currentUsername);
                AdminUserResponse response = AdminUserResponse.fromEntity(user);

                return JsonData.buildSuccess(response);
        }

        /**
         * 删除管理员用户
         */
        @RequireRole(UserRole.ADMIN) // 仅管理员可访问
        @Operation(summary = "删除管理员用户", description = "软删除管理员用户（仅管理员可操作）", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "删除成功"),
                        @ApiResponse(responseCode = "401", description = "未认证"),
                        @ApiResponse(responseCode = "403", description = "权限不足"),
                        @ApiResponse(responseCode = "404", description = "用户不存在")
        })
        @DeleteMapping("/{id}")
        public JsonData<Void> deleteAdminUser(
                        @Parameter(description = "用户ID (必填)", required = true, example = "1") @PathVariable Long id,
                        HttpServletRequest httpRequest) {

                String currentUsername = SecurityUtil.getCurrentUsername(httpRequest, token -> {
                        AdminUser user = authService.getCurrentUser(token);
                        return user != null ? user.getUsername() : null;
                });
                adminUserService.deleteAdminUser(id, currentUsername);

                return JsonData.buildSuccess();
        }

        /**
         * 获取管理员用户详情
         */
        @RequireRole(UserRole.ADMIN) // 仅管理员可访问
        @Operation(summary = "获取管理员用户详情", description = "根据ID获取管理员用户详细信息（仅管理员可操作）", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "获取成功"),
                        @ApiResponse(responseCode = "401", description = "未认证"),
                        @ApiResponse(responseCode = "403", description = "权限不足"),
                        @ApiResponse(responseCode = "404", description = "用户不存在")
        })
        @GetMapping("/{id}")
        public JsonData<AdminUserResponse> getAdminUser(
                        @Parameter(description = "用户ID (必填)", required = true, example = "1") @PathVariable Long id) {

                AdminUser user = adminUserService.getAdminUserById(id);
                if (user == null) {
                        throw new BizException(BizCodeEnum.ACCOUNT_UNREGISTER);
                }

                AdminUserResponse response = AdminUserResponse.fromEntity(user);
                return JsonData.buildSuccess(response);
        }

        /**
         * 分页查询管理员用户列表
         */
        @RequireRole(UserRole.ADMIN) // 仅管理员可访问
        @Operation(summary = "分页查询管理员用户列表", description = "分页查询管理员用户列表，支持按用户名、角色、状态筛选（仅管理员可操作）", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "查询成功"),
                        @ApiResponse(responseCode = "401", description = "未认证"),
                        @ApiResponse(responseCode = "403", description = "权限不足")
        })
        @GetMapping
        public JsonData<IPage<AdminUserResponse>> listAdminUsers(
                        @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") Integer page,
                        @Parameter(description = "每页大小", example = "10") @RequestParam(defaultValue = "10") Integer size,
                        @Parameter(description = "用户名（模糊查询）", example = "admin") @RequestParam(required = false) String username,
                        @Parameter(description = "角色: 1=管理员, 2=普通用户", example = "1") @RequestParam(required = false) Integer role,
                        @Parameter(description = "状态: 0=禁用, 1=启用", example = "1") @RequestParam(required = false) Integer status) {

                IPage<AdminUser> adminUserPage = adminUserService.listAdminUsers(page, size, username, role, status);

                // 转换为响应DTO
                Page<AdminUserResponse> responsePage = new Page<>(adminUserPage.getCurrent(), adminUserPage.getSize(),
                                adminUserPage.getTotal());
                responsePage.setRecords(
                                adminUserPage.getRecords().stream()
                                                .map(AdminUserResponse::fromEntity)
                                                .toList());

                return JsonData.buildSuccess(responsePage);
        }

        /**
         * 修改密码
         */
        @RequireRole(UserRole.ADMIN) // 仅管理员可访问
        @Operation(summary = "修改密码", description = "修改管理员用户密码（仅管理员可操作）", security = @SecurityRequirement(name = "Bearer"))
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "修改成功"),
                        @ApiResponse(responseCode = "400", description = "请求参数错误"),
                        @ApiResponse(responseCode = "401", description = "未认证"),
                        @ApiResponse(responseCode = "403", description = "权限不足"),
                        @ApiResponse(responseCode = "404", description = "用户不存在")
        })
        @PutMapping("/{id}/password")
        public JsonData<Void> changePassword(
                        @Parameter(description = "用户ID (必填)", required = true, example = "1") @PathVariable Long id,
                        @Parameter(description = "修改密码请求 (必填)", required = true) @Valid @RequestBody ChangePasswordRequest request,
                        HttpServletRequest httpRequest) {

                String currentUsername = SecurityUtil.getCurrentUsername(httpRequest, token -> {
                        AdminUser user = authService.getCurrentUser(token);
                        return user != null ? user.getUsername() : null;
                });
                adminUserService.changePassword(id, request, currentUsername);

                return JsonData.buildSuccess();
        }

}
