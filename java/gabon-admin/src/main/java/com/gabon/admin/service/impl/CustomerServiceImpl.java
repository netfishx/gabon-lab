package com.gabon.admin.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.admin.mapper.CustomerMapper;
import com.gabon.admin.model.dto.CustomerResponse;
import com.gabon.admin.model.entity.Customer;
import com.gabon.admin.service.CustomerService;
import com.gabon.admin.util.PasswordUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户服务实现
 * Customer Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerMapper customerMapper;

    @Override
    public IPage<CustomerResponse> findCustomers(int page, int size, String name, Integer isVip) {
        Page<Customer> pageParam = new Page<>(page, size);

        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();

        // Only query non-deleted records
        wrapper.isNull(Customer::getDeletedFlag);

        // Filter by name (fuzzy search)
        if (StringUtils.hasText(name)) {
            wrapper.like(Customer::getName, name);
        }

        // Filter by VIP status
        if (isVip != null) {
            wrapper.eq(Customer::getIsVip, isVip);
        }

        // Order by create time descending
        wrapper.orderByDesc(Customer::getCreateTime);

        IPage<Customer> result = customerMapper.selectPage(pageParam, wrapper);

        return result.convert(CustomerResponse::fromEntity);
    }

    @Override
    public boolean changePassword(Long customerId, String newPassword) {
        if (customerId == null || !StringUtils.hasText(newPassword)) {
            log.warn("Invalid parameters for changing password: customerId={}, newPassword is empty={}",
                    customerId, !StringUtils.hasText(newPassword));
            return false;
        }

        // Find customer by ID
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            log.warn("Customer not found with id: {}", customerId);
            return false;
        }

        // Check if customer is deleted
        if (customer.getDeletedFlag() != null) {
            log.warn("Cannot change password for deleted customer: {}", customerId);
            return false;
        }

        // Encrypt new password
        String encryptedPassword = PasswordUtil.encryptPassword(newPassword);

        // Update password
        customer.setPasswordHash(encryptedPassword);
        int updated = customerMapper.updateById(customer);

        if (updated > 0) {
            log.info("Successfully changed password for customer: {}", customerId);
            return true;
        } else {
            log.error("Failed to update password for customer: {}", customerId);
            return false;
        }
    }
}
