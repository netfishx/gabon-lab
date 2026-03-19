package com.gabon.service.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.enums.CashOrderStatusEnum;
import com.gabon.common.enums.CashOrderTypeEnum;
import com.gabon.common.exception.BizException;
import com.gabon.common.util.IDUtil;
import com.gabon.common.util.PasswordUtil;
import com.gabon.service.config.WalletProperties;
import com.gabon.service.mapper.CustomerCashOrderMapper;
import com.gabon.service.mapper.CustomerMapper;
import com.gabon.service.model.dto.CreateRechargeOrderRequest;
import com.gabon.service.model.dto.CreateWithdrawOrderRequest;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.model.entity.CustomerCashOrder;
import com.gabon.service.model.vo.CustomerCashOrderVO;
import com.gabon.service.service.CustomerCashOrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 客户资金订单服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerCashOrderServiceImpl implements CustomerCashOrderService {

    private static final List<Integer> ACTIVE_WITHDRAW_STATUSES = Arrays.asList(
            CashOrderStatusEnum.PENDING_ADMIN_REVIEW.getCode(),
            CashOrderStatusEnum.PROCESSING.getCode());

    private final CustomerCashOrderMapper customerCashOrderMapper;
    private final CustomerMapper customerMapper;
    private final WalletProperties walletProperties;

    @Override
    @Transactional
    public CustomerCashOrderVO createRechargeOrder(Long customerId, CreateRechargeOrderRequest request) {
        Customer customer = getActiveCustomer(customerId);
        BigDecimal exchangeRate = walletProperties.getExchangeRate();
        BigDecimal fiatAmount = normalizeFiatAmount(request.getFiatAmount());
        long diamondAmount = calculateRechargeDiamondAmount(fiatAmount, exchangeRate);

        CustomerCashOrder order = new CustomerCashOrder();
        order.setOrderNo(buildOrderNo(CashOrderTypeEnum.RECHARGE));
        order.setCustomerId(customer.getId());
        order.setCustomerUsername(customer.getUsername());
        order.setCustomerName(customer.getName());
        order.setOrderType(CashOrderTypeEnum.RECHARGE.getCode());
        order.setStatus(CashOrderStatusEnum.PROCESSING.getCode());
        order.setFiatAmount(fiatAmount);
        order.setDiamondAmount(diamondAmount);
        order.setCurrencyCode(walletProperties.getCurrencyCode());
        order.setExchangeRate(exchangeRate);
        order.setCreateBy(customer.getUsername());
        order.setUpdateBy(customer.getUsername());
        customerCashOrderMapper.insert(order);

        return CustomerCashOrderVO.fromEntity(order);
    }

    @Override
    @Transactional
    public CustomerCashOrderVO createWithdrawOrder(Long customerId, CreateWithdrawOrderRequest request) {
        Customer customer = getActiveCustomer(customerId);
        validateWithdrawalPassword(customer, request.getWithdrawalPassword());
        ensureNoActiveWithdrawal(customerId);

        BigDecimal exchangeRate = walletProperties.getExchangeRate();
        BigDecimal fiatAmount = normalizeFiatAmount(request.getFiatAmount());
        long diamondAmount = calculateWithdrawDiamondAmount(fiatAmount, exchangeRate);
        if (diamondAmount <= 0) {
            throw BizCodeEnum.PARAM_ERROR.format("提现金额过小");
        }

        int frozen = customerMapper.freezeDiamondBalance(customerId, diamondAmount);
        if (frozen == 0) {
            throw new BizException(BizCodeEnum.DIAMOND_BALANCE_NOT_ENOUGH);
        }

        CustomerCashOrder order = new CustomerCashOrder();
        order.setOrderNo(buildOrderNo(CashOrderTypeEnum.WITHDRAW));
        order.setCustomerId(customer.getId());
        order.setCustomerUsername(customer.getUsername());
        order.setCustomerName(customer.getName());
        order.setOrderType(CashOrderTypeEnum.WITHDRAW.getCode());
        order.setStatus(CashOrderStatusEnum.PENDING_ADMIN_REVIEW.getCode());
        order.setFiatAmount(fiatAmount);
        order.setDiamondAmount(diamondAmount);
        order.setCurrencyCode(walletProperties.getCurrencyCode());
        order.setExchangeRate(exchangeRate);
        order.setCreateBy(customer.getUsername());
        order.setUpdateBy(customer.getUsername());
        customerCashOrderMapper.insert(order);

        return CustomerCashOrderVO.fromEntity(order);
    }

    @Override
    public IPage<CustomerCashOrderVO> getCustomerCashOrders(Long customerId, Integer page, Integer size) {
        Page<CustomerCashOrder> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<CustomerCashOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerCashOrder::getCustomerId, customerId)
                .isNull(CustomerCashOrder::getDeletedFlag)
                .orderByDesc(CustomerCashOrder::getCreateTime);

        IPage<CustomerCashOrder> resultPage = customerCashOrderMapper.selectPage(pageParam, wrapper);
        Page<CustomerCashOrderVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(),
                resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream()
                .map(CustomerCashOrderVO::fromEntity)
                .collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public CustomerCashOrderVO getCustomerCashOrderDetail(Long customerId, String orderNo) {
        LambdaQueryWrapper<CustomerCashOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerCashOrder::getCustomerId, customerId)
                .eq(CustomerCashOrder::getOrderNo, orderNo)
                .isNull(CustomerCashOrder::getDeletedFlag);
        CustomerCashOrder order = customerCashOrderMapper.selectOne(wrapper);
        if (order == null) {
            throw new BizException(BizCodeEnum.CASH_ORDER_NOT_FOUND);
        }
        return CustomerCashOrderVO.fromEntity(order);
    }

    private Customer getActiveCustomer(Long customerId) {
        Customer customer = customerMapper.selectActiveById(customerId);
        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNREGISTER);
        }
        return customer;
    }

    private void validateWithdrawalPassword(Customer customer, String rawPassword) {
        if (customer.getWithdrawalPasswordHash() == null || customer.getWithdrawalPasswordHash().isBlank()) {
            throw new BizException(BizCodeEnum.WITHDRAWAL_PASSWORD_NOT_SET);
        }
        if (!PasswordUtil.verifyPassword(rawPassword, customer.getWithdrawalPasswordHash())) {
            throw new BizException(BizCodeEnum.WITHDRAWAL_PASSWORD_ERROR);
        }
    }

    private void ensureNoActiveWithdrawal(Long customerId) {
        LambdaQueryWrapper<CustomerCashOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerCashOrder::getCustomerId, customerId)
                .eq(CustomerCashOrder::getOrderType, CashOrderTypeEnum.WITHDRAW.getCode())
                .in(CustomerCashOrder::getStatus, ACTIVE_WITHDRAW_STATUSES)
                .isNull(CustomerCashOrder::getDeletedFlag)
                .last("LIMIT 1");
        CustomerCashOrder existing = customerCashOrderMapper.selectOne(wrapper);
        if (existing != null) {
            throw new BizException(BizCodeEnum.WITHDRAWAL_ORDER_PENDING);
        }
    }

    private BigDecimal normalizeFiatAmount(BigDecimal fiatAmount) {
        if (fiatAmount == null) {
            throw BizCodeEnum.PARAM_ERROR.format("金额不能为空");
        }
        return fiatAmount.setScale(2, RoundingMode.DOWN);
    }

    private long calculateRechargeDiamondAmount(BigDecimal fiatAmount, BigDecimal exchangeRate) {
        validateExchangeRate(exchangeRate);
        return fiatAmount.multiply(exchangeRate)
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }

    private long calculateWithdrawDiamondAmount(BigDecimal fiatAmount, BigDecimal exchangeRate) {
        validateExchangeRate(exchangeRate);
        return fiatAmount.multiply(exchangeRate)
                .setScale(0, RoundingMode.UP)
                .longValueExact();
    }

    private void validateExchangeRate(BigDecimal exchangeRate) {
        if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizCodeEnum.PARAM_ERROR.format("钱包汇率配置无效");
        }
    }

    private String buildOrderNo(CashOrderTypeEnum orderType) {
        String prefix = orderType == CashOrderTypeEnum.RECHARGE ? "CR" : "CW";
        return prefix + IDUtil.geneSnowFlakeID();
    }
}
