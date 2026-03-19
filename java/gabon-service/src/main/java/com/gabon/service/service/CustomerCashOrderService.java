package com.gabon.service.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.service.model.dto.CreateRechargeOrderRequest;
import com.gabon.service.model.dto.CreateWithdrawOrderRequest;
import com.gabon.service.model.vo.CustomerCashOrderVO;

public interface CustomerCashOrderService {

    CustomerCashOrderVO createRechargeOrder(Long customerId, CreateRechargeOrderRequest request);

    CustomerCashOrderVO createWithdrawOrder(Long customerId, CreateWithdrawOrderRequest request);

    IPage<CustomerCashOrderVO> getCustomerCashOrders(Long customerId, Integer page, Integer size);

    CustomerCashOrderVO getCustomerCashOrderDetail(Long customerId, String orderNo);
}
