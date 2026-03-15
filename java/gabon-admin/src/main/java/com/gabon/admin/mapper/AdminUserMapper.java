package com.gabon.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gabon.admin.model.entity.AdminUser;

import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员用户Mapper
 * Admin User Mapper
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
}
