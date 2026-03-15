package com.gabon.service.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gabon.common.enums.TransactionTypeEnum;
import com.gabon.service.model.entity.CustomerTransaction;
import com.gabon.service.model.vo.CustomerTransactionVO;
import com.gabon.service.model.vo.WalletVO;

/**
 * 客户交易记录 服务类
 */
public interface CustomerTransactionService extends IService<CustomerTransaction> {

    /**
     * 分页查询客户交易记录
     *
     * @param customerId 客户ID
     * @param page       页码
     * @param size       每页大小
     * @return 分页结果
     */
    IPage<CustomerTransactionVO> getCustomerTransactions(Long customerId, Integer page, Integer size);

    /**
     * 新增钻石并记录带业务单号的交易流水
     *
     * @param customerId      客户ID
     * @param transactionType 交易类型
     * @param amount          钻石数量
     * @param remark          备注
     * @param transactionNo   业务单号
     * @return 新的钻石余额
     */
    Long addDiamondTransaction(Long customerId, TransactionTypeEnum transactionType, Long amount, String remark,
            String transactionNo);

    /**
     * 在独立事务中新增钻石并记录带业务单号的交易流水
     */
    Long addDiamondTransactionInNewTransaction(Long customerId, TransactionTypeEnum transactionType, Long amount,
            String remark, String transactionNo);

    /**
     * 获取用户今日新增钻石数
     *
     * @param customerId 客户ID
     * @return 今日新增钻石数
     */
    Long getTodayDiamond(Long customerId);

    /**
     * 获取用户最近7日新增钻石总数
     *
     * @param customerId 客户ID
     * @return 最近7日新增钻石总数
     */
    Long getLastSevenDaysDiamond(Long customerId);

    /**
     * 获取钱包信息（总余额 + 近7天收益 + 近7天流水）
     *
     * @param customerId 客户ID
     * @return 钱包信息
     */
    WalletVO getWallet(Long customerId);
}
