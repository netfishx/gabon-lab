package com.gabon.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.admin.model.dto.CashOrderResponse;
import com.gabon.admin.model.dto.PendingWithdrawOrderResponse;

public interface CashOrderService {

    IPage<PendingWithdrawOrderResponse> getPendingWithdrawOrders(int page, int size);

    IPage<CashOrderResponse> getCashOrders(int page, int size, Integer orderType, Integer status,
            String customerKeyword, String orderNo, String startDate, String endDate);

    void reviewWithdrawOrder(Long orderId, Integer status, String remark, Long reviewerId);

    void completeCashOrder(Long orderId, Integer status, Long operatorId);
}
