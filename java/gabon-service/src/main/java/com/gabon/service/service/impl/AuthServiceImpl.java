package com.gabon.service.service.impl;

import java.time.Instant;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.enums.TransactionTypeEnum;
import com.gabon.common.exception.BizException;
import com.gabon.common.util.PasswordUtil;
import com.gabon.service.mapper.ActivityRewardConfigMapper;
import com.gabon.service.mapper.CustomerMapper;
import com.gabon.service.model.dto.ChangePasswordRequest;
import com.gabon.service.model.dto.LoginRequest;
import com.gabon.service.model.dto.LoginResponse;
import com.gabon.service.model.dto.RefreshTokenResponse;
import com.gabon.service.model.dto.RegisterRequest;
import com.gabon.service.model.entity.ActivityRewardConfig;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.service.AuthService;
import com.gabon.service.service.CustomerTransactionService;
import com.gabon.service.util.JWTUtil;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private static final int MAX_INVITE_CODE_RETRIES = 5;

    @Autowired
    CustomerMapper customerMapper;
    @Autowired
    JWTUtil jwtUtil;
    @Autowired
    ActivityRewardConfigMapper rewardConfigMapper;
    @Autowired
    CustomerTransactionService customerTransactionService;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String clientIp) {
        // 1. 根据用户名查询客户
        Customer customer = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getUsername, request.getUsername())
                        .isNull(Customer::getDeletedFlag));

        if (customer == null) {
            log.warn("Customer login failed: username not found - {}", request.getUsername());
            throw new BizException(BizCodeEnum.ACCOUNT_PWD_ERROR);
        }

        // 2. 验证密码
        if (!PasswordUtil.verifyPassword(request.getPassword(), customer.getPasswordHash())) {
            log.warn("Customer login failed: incorrect password - username: {}", request.getUsername());
            throw new BizException(BizCodeEnum.ACCOUNT_PWD_ERROR);
        }

        // 3. 更新最后登录时间
        customer.setLastLoginAt(Instant.now());
        customerMapper.updateById(customer);

        // 4. 生成JWT令牌
        String token = jwtUtil.generateToken(customer.getId(), customer.getUsername());
        Instant expiresAt = jwtUtil.getExpirationFromToken(token);

        // 5. 构建响应
        LoginResponse.CustomerInfo customerInfo = new LoginResponse.CustomerInfo();
        customerInfo.setId(customer.getId());
        customerInfo.setUsername(customer.getUsername());
        customerInfo.setName(customer.getName());
        customerInfo.setPhone(customer.getPhone());
        customerInfo.setIsVip(customer.getIsVip());
        customerInfo.setAvatarUrl(customer.getAvatarUrl());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setExpiresAt(expiresAt);
        response.setCustomerInfo(customerInfo);

        log.info("Customer login successful - Customer ID: {}, Username: {}, IP: {}",
                customer.getId(), customer.getUsername(), clientIp);

        return response;
    }

    @Override
    @Transactional
    public LoginResponse register(RegisterRequest request, String clientIp) {
        // 1. 验证密码和确认密码是否一致
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            log.warn("Customer register failed: password and confirmPassword do not match - username: {}",
                    request.getUsername());
            throw BizCodeEnum.PARAM_ERROR.format("密码和确认密码不一致");
        }

        // 2. 检查用户名是否已存在（参考 AdminUserServiceImpl.createAdminUser 的实现）
        Customer existingCustomer = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getUsername, request.getUsername())
                        .isNull(Customer::getDeletedFlag));

        if (existingCustomer != null) {
            log.warn("Customer register failed: username already exists - {}", request.getUsername());
            throw new BizException(BizCodeEnum.ACCOUNT_REPEAT);
        }

        // 2.5 校验邀请码
        Customer inviter = null;
        if (request.getInviteCode() != null && !request.getInviteCode().trim().isEmpty()) {
            String code = request.getInviteCode().trim().toUpperCase();
            inviter = customerMapper.selectOne(
                    new LambdaQueryWrapper<Customer>()
                            .eq(Customer::getInviteCode, code)
                            .isNull(Customer::getDeletedFlag));
            if (inviter == null) {
                throw new BizException(BizCodeEnum.INVITE_CODE_NOT_FOUND);
            }
        }

        // 3. 创建新客户
        Customer customer = buildNewCustomer(request, inviter);

        // 4. 插入数据库
        insertCustomerWithRetry(customer);

        // 4.5 发放邀请奖励（从配置表读取，默认200钻石）
        if (inviter != null) {
            int inviteReward = getInviteRewardDiamonds();
            try {
                customerTransactionService.addDiamondTransactionInNewTransaction(
                        inviter.getId(), TransactionTypeEnum.INVITE_REWARD,
                        (long) inviteReward, "邀请奖励: 邀请用户 " + customer.getUsername(),
                        "INVITE-" + customer.getId());
                log.info("邀请奖励发放成功 - 邀请人ID: {}, 新用户ID: {}, 奖励: {}钻石",
                        inviter.getId(), customer.getId(), inviteReward);
            } catch (Exception e) {
                log.error("邀请奖励发放失败，但注册继续成功 - inviterId: {}, newCustomerId: {}",
                        inviter.getId(), customer.getId(), e);
            }
        }

        log.info("Customer register successful - Customer ID: {}, Username: {}, IP: {}",
                customer.getId(), customer.getUsername(), clientIp);

        // 5. 生成JWT token（自动登录）
        String token = jwtUtil.generateToken(customer.getId(), customer.getUsername());
        Instant expiresAt = jwtUtil.getExpirationFromToken(token);

        // 6. 构建响应
        LoginResponse.CustomerInfo customerInfo = new LoginResponse.CustomerInfo();
        customerInfo.setId(customer.getId());
        customerInfo.setUsername(customer.getUsername());
        customerInfo.setName(customer.getName());
        customerInfo.setPhone(customer.getPhone());
        customerInfo.setIsVip(customer.getIsVip());
        customerInfo.setAvatarUrl(customer.getAvatarUrl());

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setExpiresAt(expiresAt);
        response.setCustomerInfo(customerInfo);

        return response;
    }

    @Override
    public void logout(String token) {
        // 对于无状态的JWT，登出由客户端通过移除令牌来处理
        // 如果需要，可以在这里实现令牌黑名单
        log.info("Customer logout");
    }

    @Override
    public Customer validateToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            return null;
        }

        Long customerId = jwtUtil.getCustomerIdFromToken(token);
        if (customerId == null) {
            return null;
        }

        // 从数据库获取客户信息
        Customer customer = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getId, customerId)
                        .isNull(Customer::getDeletedFlag));

        return customer;
    }

    @Override
    public Customer getCurrentCustomer(String token) {
        return validateToken(token);
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request, Long customerId) {
        // 1. 查询客户信息
        Customer customer = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getId, customerId)
                        .isNull(Customer::getDeletedFlag));

        if (customer == null) {
            log.warn("修改密码失败：客户不存在 - customerId: {}", customerId);
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        // 2. 验证旧密码
        if (!PasswordUtil.verifyPassword(request.getOldPassword(), customer.getPasswordHash())) {
            log.warn("修改密码失败：旧密码错误 - customerId: {}", customerId);
            throw new BizException(BizCodeEnum.ACCOUNT_PWD_ERROR);
        }

        // 3. 验证新密码和确认密码是否一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            log.warn("修改密码失败：新密码与确认密码不一致 - customerId: {}", customerId);
            throw new BizException(BizCodeEnum.PASSWORD_MISMATCH);
        }

        // 4. 验证新密码不能与旧密码相同
        if (PasswordUtil.verifyPassword(request.getNewPassword(), customer.getPasswordHash())) {
            log.warn("修改密码失败：新密码不能与旧密码相同 - customerId: {}", customerId);
            throw new BizException(BizCodeEnum.PASSWORD_SAME_AS_OLD);
        }

        // 5. 加密新密码
        String newPasswordHash = PasswordUtil.encryptPassword(request.getNewPassword());

        // 6. 更新数据库
        customer.setPasswordHash(newPasswordHash);
        customerMapper.updateById(customer);

        log.info("修改密码成功 - customerId: {}, username: {}",
                customer.getId(), customer.getUsername());
    }

    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final Random RANDOM = new Random();

    private Customer buildNewCustomer(RegisterRequest request, Customer inviter) {
        Customer customer = new Customer();
        customer.setUsername(request.getUsername());
        customer.setPasswordHash(PasswordUtil.encryptPassword(request.getPassword()));
        customer.setName(request.getUsername());
        customer.setIsVip(0);
        customer.setDiamondBalance(0L);
        customer.setRegistrationTime(Instant.now());
        customer.setCreateBy("system");
        customer.setCreateTime(Instant.now());
        customer.setInviteCode(generateUniqueInviteCode());
        if (inviter != null) {
            customer.setInvitedBy(inviter.getId());
        }
        return customer;
    }

    private void insertCustomerWithRetry(Customer customer) {
        for (int attempt = 0; attempt < MAX_INVITE_CODE_RETRIES; attempt++) {
            try {
                customerMapper.insert(customer);
                return;
            } catch (DataIntegrityViolationException e) {
                if (isUsernameDuplicate(e)) {
                    throw new BizException(BizCodeEnum.ACCOUNT_REPEAT);
                }
                if (isInviteCodeDuplicate(e)) {
                    customer.setInviteCode(generateUniqueInviteCode());
                    continue;
                }
                throw e;
            }
        }

        log.error("Customer register failed: invite code collision retries exhausted - username: {}",
                customer.getUsername());
        throw BizCodeEnum.PARAM_ERROR.format("邀请码生成失败，请重试");
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
            for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
                sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (customerMapper.selectCount(
                new LambdaQueryWrapper<Customer>().eq(Customer::getInviteCode, code)) > 0);
        return code;
    }

    /**
     * 从配置表获取邀请奖励钻石数（默认200）
     */
    private int getInviteRewardDiamonds() {
        LambdaQueryWrapper<ActivityRewardConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActivityRewardConfig::getConfigType, "INVITE_REWARD")
                .eq(ActivityRewardConfig::getConfigKey, "invite")
                .eq(ActivityRewardConfig::getStatus, 1)
                .isNull(ActivityRewardConfig::getDeletedFlag);

        ActivityRewardConfig config = rewardConfigMapper.selectOne(wrapper);
        return config != null ? config.getRewardDiamonds() : 200;
    }

    private boolean isUsernameDuplicate(Exception e) {
        String message = e.getMessage();
        return message != null && message.toLowerCase().contains("username");
    }

    private boolean isInviteCodeDuplicate(Exception e) {
        String message = e.getMessage();
        return message != null && message.toLowerCase().contains("invite_code");
    }

    @Override
    public RefreshTokenResponse refreshToken(String token) {
        // 1. 解析Token（忽略过期时间，只验证签名）
        // 即使 token 已过期，签名有效即可刷新，避免用户被强制重新登录
        Claims claims = jwtUtil.parseTokenIgnoreExpiry(token);
        if (claims == null) {
            log.warn("刷新令牌失败：令牌签名无效");
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        // 2. 获取用户信息
        Long customerId = claims.get("customerId", Long.class);
        String username = claims.getSubject();

        if (customerId == null || username == null) {
            log.warn("刷新令牌失败：令牌缺少客户信息");
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        // 3. 验证用户是否存在
        Customer customer = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>()
                        .eq(Customer::getId, customerId)
                        .isNull(Customer::getDeletedFlag));

        if (customer == null) {
            log.warn("刷新令牌失败：客户不存在 - customerId: {}", customerId);
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }

        // 4. 生成新Token
        String newToken = jwtUtil.generateToken(customer.getId(), customer.getUsername());
        Instant expiresAt = jwtUtil.getExpirationFromToken(newToken);

        log.info("刷新令牌成功 - customerId: {}, username: {}",
                customer.getId(), customer.getUsername());

        // 5. 构建响应
        RefreshTokenResponse response = new RefreshTokenResponse();
        response.setToken(newToken);
        response.setTokenType("Bearer");
        response.setExpiresAt(expiresAt);
        return response;
    }
}
