package com.gabon.service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.common.enums.BizCodeEnum;
import com.gabon.common.exception.BizException;
import com.gabon.common.util.JsonData;
import com.gabon.service.config.AuthInterceptor;
import com.gabon.service.model.dto.CreateRechargeOrderRequest;
import com.gabon.service.model.dto.CreateWithdrawOrderRequest;
import com.gabon.service.model.entity.Customer;
import com.gabon.service.model.vo.CustomerCashOrderVO;
import com.gabon.service.service.CustomerCashOrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 资金订单控制器
 */
@RestController
@RequestMapping("/api/cash-orders")
@Tag(name = "资金订单", description = "充值、提现及资金订单查询相关接口")
public class CashOrderController {

    @Autowired
    CustomerCashOrderService customerCashOrderService;

    @Operation(summary = "创建充值订单", description = "客户输入CNY金额创建充值订单，等待后续第三方支付完成。", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @PostMapping("/recharge")
    public JsonData<CustomerCashOrderVO> createRechargeOrder(@Valid @RequestBody CreateRechargeOrderRequest request) {
        Customer customer = AuthInterceptor.threadLocal.get();
        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }
        return JsonData.buildSuccess(customerCashOrderService.createRechargeOrder(customer.getId(), request));
    }

    @Operation(summary = "创建提现订单", description = "客户输入CNY提现金额和取款密码，系统冻结对应钻石并进入管理员审核。", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @PostMapping("/withdraw")
    public JsonData<CustomerCashOrderVO> createWithdrawOrder(@Valid @RequestBody CreateWithdrawOrderRequest request) {
        Customer customer = AuthInterceptor.threadLocal.get();
        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }
        return JsonData.buildSuccess(customerCashOrderService.createWithdrawOrder(customer.getId(), request));
    }

    @Operation(summary = "获取我的资金订单列表", description = "分页获取当前客户的充值/提现订单。", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @GetMapping
    public JsonData<IPage<CustomerCashOrderVO>> getMyCashOrders(
            @Parameter(description = "页码 (从1开始)", example = "1") @RequestParam(value = "page", defaultValue = "1") Integer page,
            @Parameter(description = "每页数量", example = "10") @RequestParam(value = "size", defaultValue = "10") Integer size) {
        Customer customer = AuthInterceptor.threadLocal.get();
        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }
        return JsonData.buildSuccess(customerCashOrderService.getCustomerCashOrders(customer.getId(), page, size));
    }

    @Operation(summary = "获取我的资金订单详情", description = "根据订单号获取当前客户自己的充值/提现订单详情。", security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效"),
            @ApiResponse(responseCode = "404", description = "订单不存在")
    })
    @GetMapping("/{orderNo}")
    public JsonData<CustomerCashOrderVO> getMyCashOrderDetail(
            @Parameter(description = "订单号", example = "CW1773610000000") @PathVariable String orderNo) {
        Customer customer = AuthInterceptor.threadLocal.get();
        if (customer == null) {
            throw new BizException(BizCodeEnum.ACCOUNT_UNLOGIN);
        }
        return JsonData.buildSuccess(customerCashOrderService.getCustomerCashOrderDetail(customer.getId(), orderNo));
    }
}
