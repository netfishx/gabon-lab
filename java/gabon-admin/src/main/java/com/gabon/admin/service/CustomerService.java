package com.gabon.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.admin.model.dto.CustomerResponse;

/**
 * 客户服务接口
 * Customer Service Interface
 */
public interface CustomerService {

    /**
     * 分页查询客户列表
     * 
     * @param page  页码
     * @param size  每页大小
     * @param name  客户名称（可选，模糊查询）
     * @param isVip VIP状态（可选）: 0=non-VIP, 1=VIP
     * @return 客户分页数据
     */
    IPage<CustomerResponse> findCustomers(int page, int size, String name, Integer isVip);

    /**
     * 修改客户密码
     * 
     * @param customerId  客户ID
     * @param newPassword 新密码（明文）
     * @return 是否修改成功
     */
    boolean changePassword(Long customerId, String newPassword);
}
