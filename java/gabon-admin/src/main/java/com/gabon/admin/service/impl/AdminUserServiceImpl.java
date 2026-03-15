package com.gabon.admin.service.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.admin.mapper.AdminUserMapper;
import com.gabon.admin.model.dto.ChangePasswordRequest;
import com.gabon.admin.model.dto.CreateAdminUserRequest;
import com.gabon.admin.model.dto.UpdateAdminUserRequest;
import com.gabon.admin.model.entity.AdminUser;
import com.gabon.admin.service.AdminUserService;
import com.gabon.common.util.PasswordUtil;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.exception.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 管理员用户服务实现
 * Admin User Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserMapper adminUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUser createAdminUser(CreateAdminUserRequest request, String currentUsername) {
        // 检查用户名是否已存在
        if (isUsernameExists(request.getUsername())) {
            throw new BizException(BizCodeEnum.ACCOUNT_REPEAT);
        }

        // 验证密码强度
        if (!PasswordUtil.isStrongPassword(request.getPassword())) {
            throw new BizException(BizCodeEnum.PASSWORD_WEAK);
        }

        // 验证角色值
        if (request.getRole() != 1 && request.getRole() != 2) {
            throw new BizException(BizCodeEnum.INVALID_ROLE);
        }

        // 创建用户实体
        AdminUser user = new AdminUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(PasswordUtil.encryptPassword(request.getPassword()));
        user.setRole(request.getRole());
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setStatus(request.getStatus() != null ? request.getStatus() : 1);
        user.setCreateBy(currentUsername);
        user.setUpdateBy(currentUsername);

        adminUserMapper.insert(user);
        log.info("创建管理员用户成功: username={}, id={}, createdBy={}", user.getUsername(), user.getId(), currentUsername);

        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUser updateAdminUser(Long id, UpdateAdminUserRequest request, String currentUsername) {
        // 查询用户
        AdminUser user = getAdminUserById(id);
        if (user == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNREGISTER);
        }

        // 验证角色值
        if (request.getRole() != null && request.getRole() != 1 && request.getRole() != 2) {
            throw new BizException(BizCodeEnum.INVALID_ROLE);
        }

        // 更新字段
        if (StringUtils.hasText(request.getFullName())) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        user.setUpdateBy(currentUsername);

        adminUserMapper.updateById(user);
        log.info("更新管理员用户成功: id={}, username={}, updatedBy={}", id, user.getUsername(), currentUsername);

        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAdminUser(Long id, String currentUsername) {
        // 查询用户
        AdminUser user = getAdminUserById(id);
        if (user == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNREGISTER);
        }

        // 软删除
        user.setDeletedFlag(Instant.now());
        user.setUpdateBy(currentUsername);
        adminUserMapper.updateById(user);

        log.info("删除管理员用户成功: id={}, username={}, deletedBy={}", id, user.getUsername(), currentUsername);
    }

    @Override
    public AdminUser getAdminUserById(Long id) {
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminUser::getId, id)
                .isNull(AdminUser::getDeletedFlag);
        return adminUserMapper.selectOne(wrapper);
    }

    @Override
    public IPage<AdminUser> listAdminUsers(Integer page, Integer size, String username, Integer role,
            Integer status) {
        // 构建查询条件
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(AdminUser::getDeletedFlag);

        if (StringUtils.hasText(username)) {
            wrapper.like(AdminUser::getUsername, username);
        }
        if (role != null) {
            wrapper.eq(AdminUser::getRole, role);
        }
        if (status != null) {
            wrapper.eq(AdminUser::getStatus, status);
        }

        wrapper.orderByDesc(AdminUser::getCreateTime);

        // 分页查询
        Page<AdminUser> pageObj = new Page<>(page, size);
        return adminUserMapper.selectPage(pageObj, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long id, ChangePasswordRequest request, String currentUsername) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null || user.getDeletedFlag() != null) { // Reverted to original check for deletedFlag
            throw new BizException(BizCodeEnum.ACCOUNT_UNREGISTER);
        }

        // 直接设置新密码
        user.setPasswordHash(PasswordUtil.encryptPassword(request.getNewPassword()));
        user.setUpdateBy(currentUsername);
        // user.setUpdatedAt(Instant.now()); // Removed as AdminUser doesn't have
        // setUpdatedAt, setUpdateTime is handled by MybatisPlus

        adminUserMapper.updateById(user);
        log.info("修改密码成功: id={}, username={}, changedBy={}", id, user.getUsername(), currentUsername); // Reverted log
                                                                                                       // message
    }

    @Override
    public boolean isUsernameExists(String username) {
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminUser::getUsername, username)
                .isNull(AdminUser::getDeletedFlag);
        return adminUserMapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean isUsernameExists(String username, Long excludeId) {
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminUser::getUsername, username)
                .ne(AdminUser::getId, excludeId)
                .isNull(AdminUser::getDeletedFlag);
        return adminUserMapper.selectCount(wrapper) > 0;
    }
}
