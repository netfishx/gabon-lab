package com.gabon.admin.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gabon.admin.mapper.CustomerCashOrderMapper;
import com.gabon.admin.mapper.CustomerMapper;
import com.gabon.admin.mapper.CustomerTransactionMapper;
import com.gabon.admin.mapper.AdminUserMapper;
import com.gabon.admin.model.dto.CashOrderResponse;
import com.gabon.admin.model.dto.PendingWithdrawOrderResponse;
import com.gabon.admin.model.entity.AdminUser;
import com.gabon.admin.model.entity.CustomerCashOrder;
import com.gabon.admin.model.entity.CustomerTransaction;
import com.gabon.admin.service.CashOrderService;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.enums.CashOrderStatusEnum;
import com.gabon.common.enums.CashOrderTypeEnum;
import com.gabon.common.enums.TransactionTypeEnum;
import com.gabon.common.exception.BizException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashOrderServiceImpl implements CashOrderService {

    private final CustomerCashOrderMapper customerCashOrderMapper;
    private final CustomerMapper customerMapper;
    private final CustomerTransactionMapper customerTransactionMapper;
    private final AdminUserMapper adminUserMapper;

    @Override
    public IPage<PendingWithdrawOrderResponse> getPendingWithdrawOrders(int page, int size) {
        Page<CustomerCashOrder> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<CustomerCashOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(CustomerCashOrder::getDeletedFlag)
                .eq(CustomerCashOrder::getOrderType, CashOrderTypeEnum.WITHDRAW.getCode())
                .eq(CustomerCashOrder::getStatus, CashOrderStatusEnum.PENDING_ADMIN_REVIEW.getCode())
                .orderByDesc(CustomerCashOrder::getCreateTime);

        IPage<CustomerCashOrder> pageResult = customerCashOrderMapper.selectPage(pageParam, wrapper);
        Page<PendingWithdrawOrderResponse> responsePage = new Page<>(pageResult.getCurrent(), pageResult.getSize(),
                pageResult.getTotal());
        responsePage.setRecords(pageResult.getRecords().stream()
                .map(PendingWithdrawOrderResponse::fromEntity)
                .collect(Collectors.toList()));
        return responsePage;
    }

    @Override
    public IPage<CashOrderResponse> getCashOrders(int page, int size, Integer orderType, Integer status,
            String customerKeyword, String orderNo, String startDate, String endDate) {
        return queryCashOrders(page, size, orderType, status, customerKeyword, orderNo, startDate, endDate);
    }

    @Override
    @Transactional
    public void reviewWithdrawOrder(Long orderId, Integer status, String remark, Long reviewerId) {
        if (status == null || (status != 1 && status != 2)) {
            throw BizCodeEnum.PARAM_ERROR.format("审核状态仅支持 1=同意, 2=拒绝");
        }

        CustomerCashOrder order = customerCashOrderMapper.selectById(orderId);
        if (order == null || order.getDeletedFlag() != null) {
            throw new BizException(BizCodeEnum.CASH_ORDER_NOT_FOUND);
        }
        if (order.getOrderType() == null || order.getOrderType() != CashOrderTypeEnum.WITHDRAW.getCode()) {
            throw new BizException(BizCodeEnum.CASH_ORDER_STATUS_ERROR);
        }
        if (order.getStatus() == null || order.getStatus() != CashOrderStatusEnum.PENDING_ADMIN_REVIEW.getCode()) {
            throw new BizException(BizCodeEnum.CASH_ORDER_STATUS_ERROR);
        }

        order.setReviewedByAdminId(reviewerId);
        order.setReviewedTime(Instant.now());
        order.setUpdateBy(String.valueOf(reviewerId));
        if (StringUtils.hasText(remark)) {
            order.setFailureReason(remark);
        }

        if (status == 2) {
            int released = customerMapper.releaseFrozenDiamondBalance(order.getCustomerId(), order.getDiamondAmount());
            if (released == 0) {
                // throw new BizException(BizCodeEnum.CASH_ORDER_STATUS_ERROR);
                // FIXME just log error
                log.error("Failed to release frozen diamond balance for order: {}", orderId);
            }
            order.setStatus(CashOrderStatusEnum.REJECTED.getCode());
            order.setFailureReason(StringUtils.hasText(remark) ? remark : "管理员审核拒绝");
            order.setCompletedTime(Instant.now());
        } else {
            order.setStatus(CashOrderStatusEnum.PROCESSING.getCode());
        }

        customerCashOrderMapper.updateById(order);
    }

    @Override
    @Transactional
    public void completeCashOrder(Long orderId, Integer status, Long operatorId) {
        if (status == null || (status != CashOrderStatusEnum.SUCCESS.getCode()
                && status != CashOrderStatusEnum.FAILED.getCode())) {
            throw BizCodeEnum.PARAM_ERROR.format("完成状态仅支持 4=成功, 5=失败");
        }

        CustomerCashOrder order = customerCashOrderMapper.selectById(orderId);
        if (order == null || order.getDeletedFlag() != null) {
            throw new BizException(BizCodeEnum.CASH_ORDER_NOT_FOUND);
        }

        if (order.getOrderType() == CashOrderTypeEnum.RECHARGE.getCode()) {
            completeRechargeOrder(order, status, operatorId);
        } else if (order.getOrderType() == CashOrderTypeEnum.WITHDRAW.getCode()) {
            completeWithdrawOrder(order, status, operatorId);
        } else {
            throw new BizException(BizCodeEnum.CASH_ORDER_STATUS_ERROR);
        }
    }

    private IPage<CashOrderResponse> queryCashOrders(int page, int size, Integer orderType, Integer status,
            String customerKeyword, String orderNo, String startDate, String endDate) {
        Page<CustomerCashOrder> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<CustomerCashOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(CustomerCashOrder::getDeletedFlag);

        if (orderType != null) {
            wrapper.eq(CustomerCashOrder::getOrderType, orderType);
        }
        if (status != null) {
            wrapper.eq(CustomerCashOrder::getStatus, status);
        }
        if (StringUtils.hasText(orderNo)) {
            wrapper.eq(CustomerCashOrder::getOrderNo, orderNo.trim());
        }
        if (StringUtils.hasText(customerKeyword)) {
            wrapper.and(w -> w.like(CustomerCashOrder::getCustomerUsername, customerKeyword.trim())
                    .or()
                    .like(CustomerCashOrder::getCustomerName, customerKeyword.trim()));
        }
        if (StringUtils.hasText(startDate)) {
            try {
                LocalDate localDate = LocalDate.parse(startDate);
                wrapper.ge(CustomerCashOrder::getCreateTime,
                        localDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant());
            } catch (Exception ex) {
                throw BizCodeEnum.PARAM_ERROR.format("startDate 格式必须为 yyyy-MM-dd");
            }
        }
        if (StringUtils.hasText(endDate)) {
            try {
                LocalDate localDate = LocalDate.parse(endDate);
                wrapper.le(CustomerCashOrder::getCreateTime,
                        localDate.atTime(LocalTime.MAX).atZone(ZoneId.of("Asia/Shanghai")).toInstant());
            } catch (Exception ex) {
                throw BizCodeEnum.PARAM_ERROR.format("endDate 格式必须为 yyyy-MM-dd");
            }
        }

        wrapper.orderByDesc(CustomerCashOrder::getCreateTime);

        IPage<CustomerCashOrder> pageResult = customerCashOrderMapper.selectPage(pageParam, wrapper);
        Page<CashOrderResponse> responsePage = new Page<>(pageResult.getCurrent(), pageResult.getSize(),
                pageResult.getTotal());
        responsePage.setRecords(pageResult.getRecords().stream()
                .map(this::toCashOrderResponse)
                .collect(Collectors.toList()));
        return responsePage;
    }

    private CashOrderResponse toCashOrderResponse(CustomerCashOrder order) {
        String reviewedByAdminUsername = null;
        if (order.getReviewedByAdminId() != null) {
            AdminUser adminUser = adminUserMapper.selectById(order.getReviewedByAdminId());
            if (adminUser != null) {
                reviewedByAdminUsername = adminUser.getUsername();
            }
        }

        return CashOrderResponse.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .customerId(order.getCustomerId())
                .customerUsername(order.getCustomerUsername())
                .customerName(order.getCustomerName())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .reviewedByAdminId(order.getReviewedByAdminId())
                .reviewedByAdminUsername(reviewedByAdminUsername)
                .amount(order.getFiatAmount())
                .currency(order.getCurrencyCode())
                .thirdPartyOrderNo(order.getProviderOrderNo())
                .reviewedTime(order.getReviewedTime())
                .remark(order.getFailureReason())
                .completedTime(order.getCompletedTime())
                .createTime(order.getCreateTime())
                .build();
    }

    private void completeRechargeOrder(CustomerCashOrder order, Integer status, Long operatorId) {
        if (order.getStatus() == null || (order.getStatus() != CashOrderStatusEnum.PENDING_ADMIN_REVIEW.getCode()
                && order.getStatus() != CashOrderStatusEnum.PROCESSING.getCode())) {
            throw new BizException(BizCodeEnum.CASH_ORDER_STATUS_ERROR);
        }

        order.setStatus(status);
        order.setCompletedTime(Instant.now());
        order.setUpdateBy(String.valueOf(operatorId));

        if (status == CashOrderStatusEnum.SUCCESS.getCode()) {
            int updated = customerMapper.addDiamondBalance(order.getCustomerId(), order.getDiamondAmount());
            if (updated == 0) {
                throw new BizException(BizCodeEnum.CASH_ORDER_STATUS_ERROR);
            }
            saveTransaction(order, TransactionTypeEnum.RECHARGE, "手动充值到账");
            order.setProviderStatus("MANUAL_SUCCESS");
            order.setFailureReason(null);
        } else {
            order.setProviderStatus("MANUAL_FAILED");
            order.setFailureReason("手动充值失败");
        }

        customerCashOrderMapper.updateById(order);
    }

    private void completeWithdrawOrder(CustomerCashOrder order, Integer status, Long operatorId) {
        if (order.getStatus() == null || order.getStatus() != CashOrderStatusEnum.PROCESSING.getCode()) {
            throw new BizException(BizCodeEnum.CASH_ORDER_STATUS_ERROR);
        }

        order.setStatus(status);
        order.setCompletedTime(Instant.now());
        order.setUpdateBy(String.valueOf(operatorId));

        if (status == CashOrderStatusEnum.SUCCESS.getCode()) {
            int settled = customerMapper.settleWithdrawSuccess(order.getCustomerId(), order.getDiamondAmount());
            if (settled == 0) {
                throw new BizException(BizCodeEnum.CASH_ORDER_STATUS_ERROR);
            }
            saveTransaction(order, TransactionTypeEnum.WITHDRAW, "手动提现完成");
            order.setProviderStatus("MANUAL_SUCCESS");
            order.setFailureReason(null);
        } else {
            int released = customerMapper.releaseFrozenDiamondBalance(order.getCustomerId(), order.getDiamondAmount());
            if (released == 0) {
                throw new BizException(BizCodeEnum.CASH_ORDER_STATUS_ERROR);
            }
            order.setProviderStatus("MANUAL_FAILED");
            order.setFailureReason("手动提现失败");
        }

        customerCashOrderMapper.updateById(order);
    }

    private void saveTransaction(CustomerCashOrder order, TransactionTypeEnum transactionType, String remark) {
        CustomerTransaction transaction = new CustomerTransaction();
        transaction.setCustomerId(order.getCustomerId());
        transaction.setTransactionType(transactionType.getCode());
        transaction.setAmount(order.getDiamondAmount());
        transaction.setStatus(2);
        transaction.setPaymentMethod("manual_mock");
        transaction.setTransactionNo(order.getOrderNo());
        transaction.setRemark(remark);
        transaction.setTransactionTime(Instant.now());
        transaction.setCreateBy("system");
        transaction.setUpdateBy("system");
        customerTransactionMapper.insert(transaction);
    }
}
