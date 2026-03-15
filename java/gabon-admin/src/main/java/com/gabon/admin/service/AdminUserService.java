package com.gabon.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.admin.model.dto.ChangePasswordRequest;
import com.gabon.admin.model.dto.CreateAdminUserRequest;
import com.gabon.admin.model.dto.UpdateAdminUserRequest;
import com.gabon.admin.model.entity.AdminUser;

/**
 * 管理员用户服务接口
 * Admin User Service Interface
 */
public interface AdminUserService {

    /**
     * 创建管理员用户
     * 
     * @param request         创建请求
     * @param currentUsername 当前操作用户
     * @return 创建的用户
     */
    AdminUser createAdminUser(CreateAdminUserRequest request, String currentUsername);

    /**
     * 更新管理员用户
     * 
     * @param id              用户ID
     * @param request         更新请求
     * @param currentUsername 当前操作用户
     * @return 更新后的用户
     */
    AdminUser updateAdminUser(Long id, UpdateAdminUserRequest request, String currentUsername);

    /**
     * 删除管理员用户（软删除）
     * 
     * @param id              用户ID
     * @param currentUsername 当前操作用户
     */
    void deleteAdminUser(Long id, String currentUsername);

    /**
     * 根据ID获取管理员用户
     * 
     * @param id 用户ID
     * @return 用户信息
     */
    AdminUser getAdminUserById(Long id);

    /**
     * 分页查询管理员用户
     * 
     * @param page     页码
     * @param size     每页大小
     * @param username 用户名（可选）
     * @param role     角色（可选）
     * @param status   状态（可选）
     * @return 分页结果
     */
    IPage<AdminUser> listAdminUsers(Integer page, Integer size, String username, Integer role, Integer status);

    /**
     * 修改密码
     * 
     * @param id              用户ID
     * @param request         修改密码请求
     * @param currentUsername 当前操作用户
     */
    void changePassword(Long id, ChangePasswordRequest request, String currentUsername);

    /**
     * 检查用户名是否存在
     * 
     * @param username 用户名
     * @return 是否存在
     */
    boolean isUsernameExists(String username);

    /**
     * 检查用户名是否存在（排除指定ID）
     * 
     * @param username  用户名
     * @param excludeId 排除的用户ID
     * @return 是否存在
     */
    boolean isUsernameExists(String username, Long excludeId);
}
