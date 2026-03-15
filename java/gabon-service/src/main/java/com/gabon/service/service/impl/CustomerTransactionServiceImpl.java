package com.gabon.service.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gabon.common.enums.TransactionTypeEnum;
import com.gabon.service.mapper.CustomerMapper;
import com.gabon.service.mapper.CustomerTransactionMapper;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.model.entity.CustomerTransaction;
import com.gabon.service.model.vo.CustomerTransactionVO;
import com.gabon.service.model.vo.WalletVO;
import com.gabon.service.service.CustomerTransactionService;

/**
 * 客户交易记录 服务实现类
 */
@Service
public class CustomerTransactionServiceImpl extends ServiceImpl<CustomerTransactionMapper, CustomerTransaction> implements CustomerTransactionService {

    @Autowired
    CustomerMapper customerMapper;

    @Override
    @Transactional
    public Long addDiamondTransaction(Long customerId, TransactionTypeEnum transactionType, Long amount, String remark,
            String transactionNo) {
        // Atomic balance update — avoids read-modify-write race conditions
        int updated = customerMapper.addDiamondBalance(customerId, amount);
        if (updated == 0) {
            throw new com.gabon.common.exception.BizException(com.gabon.common.enums.BizCodeEnum.ACCOUNT_UNREGISTER);
        }
        // Re-fetch the new balance to return it
        Customer customer = customerMapper.selectOne(
                new LambdaQueryWrapper<Customer>()
                        .select(Customer::getDiamondBalance)
                        .eq(Customer::getId, customerId)
                        .isNull(Customer::getDeletedFlag));
        long newBalance = customer != null && customer.getDiamondBalance() != null ? customer.getDiamondBalance() : 0L;

        CustomerTransaction transaction = new CustomerTransaction();
        transaction.setCustomerId(customerId);
        transaction.setTransactionType(transactionType.getCode());
        transaction.setAmount(amount);
        transaction.setStatus(2); // 2=成功
        transaction.setTransactionNo(transactionNo);
        transaction.setRemark(remark);
        transaction.setTransactionTime(Instant.now());
        transaction.setCreateBy("system");
        this.save(transaction);

        return newBalance;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long addDiamondTransactionInNewTransaction(Long customerId, TransactionTypeEnum transactionType, Long amount,
            String remark, String transactionNo) {
        return addDiamondTransaction(customerId, transactionType, amount, remark, transactionNo);
    }

    @Override
    public IPage<CustomerTransactionVO> getCustomerTransactions(Long customerId, Integer page, Integer size) {
        Page<CustomerTransaction> pageParam = new Page<>(page, size);
        
        LambdaQueryWrapper<CustomerTransaction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CustomerTransaction::getCustomerId, customerId)
                    .orderByDesc(CustomerTransaction::getTransactionTime);
                    
        Page<CustomerTransaction> resultPage = this.page(pageParam, queryWrapper);
        
        // 转换实体为VO
        List<CustomerTransactionVO> voList = resultPage.getRecords().stream().map(tx -> {
            CustomerTransactionVO vo = new CustomerTransactionVO();
            vo.setId(tx.getId());
            vo.setTransactionType(tx.getTransactionType());
            vo.setAmount(tx.getAmount());
            vo.setStatus(tx.getStatus());
            vo.setPaymentMethod(tx.getPaymentMethod());
            vo.setTransactionNo(tx.getTransactionNo());
            vo.setRemark(tx.getRemark());
            vo.setTransactionTime(tx.getTransactionTime());
            return vo;
        }).collect(Collectors.toList());
        
        Page<CustomerTransactionVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(voList);
        
        return voPage;
    }

    @Override
    public Long getTodayDiamond(Long customerId) {
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        Instant startOfDay = now.toLocalDate().atStartOfDay(zoneId).toInstant();
        Instant endOfDay = now.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant();

        // 2=观看奖励, 3=任务奖励, 4=签到奖励, 5=邀请奖励（不含1=充值）
        List<Integer> types = Arrays.asList(2, 3, 4, 5);

        LambdaQueryWrapper<CustomerTransaction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(CustomerTransaction::getAmount)
                .eq(CustomerTransaction::getCustomerId, customerId)
                .eq(CustomerTransaction::getStatus, 2)
                .in(CustomerTransaction::getTransactionType, types)
                .ge(CustomerTransaction::getTransactionTime, startOfDay)
                .lt(CustomerTransaction::getTransactionTime, endOfDay);

        List<Object> amounts = this.listObjs(queryWrapper);
        if (amounts == null || amounts.isEmpty()) {
            return 0L;
        }

        return amounts.stream()
                .filter(obj -> obj != null)
                .mapToLong(obj -> ((Number) obj).longValue())
                .sum();
    }

    @Override
    public Long getLastSevenDaysDiamond(Long customerId) {
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zoneId);
        Instant start = today.minusDays(6).atStartOfDay(zoneId).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zoneId).toInstant();

        // 2=观看奖励, 3=任务奖励, 4=签到奖励, 5=邀请奖励（不含1=充值）
        List<Integer> types = Arrays.asList(2, 3, 4, 5);

        LambdaQueryWrapper<CustomerTransaction> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(CustomerTransaction::getAmount)
                .eq(CustomerTransaction::getCustomerId, customerId)
                .eq(CustomerTransaction::getStatus, 2)
                .in(CustomerTransaction::getTransactionType, types)
                .ge(CustomerTransaction::getTransactionTime, start)
                .lt(CustomerTransaction::getTransactionTime, end);

        List<Object> amounts = this.listObjs(queryWrapper);
        if (amounts == null || amounts.isEmpty()) {
            return 0L;
        }
        return amounts.stream()
                .filter(obj -> obj != null)
                .mapToLong(obj -> ((Number) obj).longValue())
                .sum();
    }

    @Override
    public WalletVO getWallet(Long customerId) {
        ZoneId zoneId = ZoneId.of("Asia/Shanghai");
        LocalDate today = LocalDate.now(zoneId);
        Instant start = today.minusDays(6).atStartOfDay(zoneId).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zoneId).toInstant();

        // 总余额
        Customer customer = customerMapper.selectActiveById(customerId);
        long totalBalance = customer != null && customer.getDiamondBalance() != null
                ? customer.getDiamondBalance() : 0L;

        // 近7天收益：2=观看奖励, 3=任务奖励, 4=签到奖励, 5=邀请奖励
        List<Integer> earningTypes = Arrays.asList(2, 3, 4, 5);
        LambdaQueryWrapper<CustomerTransaction> earningsWrapper = new LambdaQueryWrapper<>();
        earningsWrapper.select(CustomerTransaction::getAmount)
                .eq(CustomerTransaction::getCustomerId, customerId)
                .eq(CustomerTransaction::getStatus, 2)
                .in(CustomerTransaction::getTransactionType, earningTypes)
                .ge(CustomerTransaction::getTransactionTime, start)
                .lt(CustomerTransaction::getTransactionTime, end);
        List<Object> earningAmounts = this.listObjs(earningsWrapper);
        long sevenDayEarnings = earningAmounts == null ? 0L : earningAmounts.stream()
                .filter(obj -> obj != null)
                .mapToLong(obj -> ((Number) obj).longValue())
                .sum();

        // 近7天全部流水
        LambdaQueryWrapper<CustomerTransaction> txWrapper = new LambdaQueryWrapper<>();
        txWrapper.eq(CustomerTransaction::getCustomerId, customerId)
                .eq(CustomerTransaction::getStatus, 2)
                .ge(CustomerTransaction::getTransactionTime, start)
                .lt(CustomerTransaction::getTransactionTime, end)
                .orderByDesc(CustomerTransaction::getTransactionTime);

        List<CustomerTransactionVO> transactions = this.list(txWrapper).stream().map(tx -> {
            CustomerTransactionVO vo = new CustomerTransactionVO();
            vo.setId(tx.getId());
            vo.setTransactionType(tx.getTransactionType());
            vo.setAmount(tx.getAmount());
            vo.setStatus(tx.getStatus());
            vo.setTransactionNo(tx.getTransactionNo());
            vo.setRemark(tx.getRemark());
            vo.setTransactionTime(tx.getTransactionTime());
            return vo;
        }).collect(Collectors.toList());

        WalletVO walletVO = new WalletVO();
        walletVO.setTotalBalance(totalBalance);
        walletVO.setSevenDayEarnings(sevenDayEarnings);
        walletVO.setTransactions(transactions);
        return walletVO;
    }
}
