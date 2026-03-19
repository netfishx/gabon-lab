package com.gabon.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gabon.admin.model.dto.CashOrderResponse;
import com.gabon.admin.model.dto.CompleteCashOrderRequest;
import com.gabon.admin.model.dto.PendingWithdrawOrderResponse;
import com.gabon.admin.model.dto.ReviewCashOrderRequest;
import com.gabon.admin.model.entity.AdminUser;
import com.gabon.admin.service.AuthService;
import com.gabon.admin.service.CashOrderService;
import com.gabon.common.util.JsonData;
import com.gabon.common.util.SecurityUtil;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cash-orders")
@RequiredArgsConstructor
@Tag(name = "资金订单管理", description = "充值/提现订单审核与查询")
public class CashOrderController {

    private final CashOrderService cashOrderService;
    private final AuthService authService;

    @Operation(summary = "查询待审核提现订单", description = "提现审核页使用，只返回待审核提现订单。",
            security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @GetMapping("/withdraw/pending")
    public JsonData<IPage<PendingWithdrawOrderResponse>> getPendingWithdrawOrders(
            @Parameter(description = "页码，从1开始", example = "1") @RequestParam(name = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量", example = "10") @RequestParam(name = "size", defaultValue = "10") int size) {
        return JsonData.buildSuccess(cashOrderService.getPendingWithdrawOrders(page, size));
    }

    @Operation(summary = "分页查询资金订单", description = "交易记录页使用，返回充值与提现订单。",
            security = @SecurityRequirement(name = "Bearer"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未认证或令牌无效")
    })
    @GetMapping
    public JsonData<IPage<CashOrderResponse>> getCashOrders(
            @Parameter(description = "页码，从1开始", example = "1") @RequestParam(name = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量", example = "10") @RequestParam(name = "size", defaultValue = "10") int size,
            @Parameter(description = "订单类型: 1=提现, 2=充值", example = "1") @RequestParam(name = "orderType", required = false) Integer orderType,
            @Parameter(description = "订单状态", example = "4") @RequestParam(name = "status", required = false) Integer status,
            @Parameter(description = "客户关键字，匹配用户名或昵称", example = "zhangsan") @RequestParam(name = "customerKeyword", required = false) String customerKeyword,
            @Parameter(description = "订单号", example = "CW1773610000000") @RequestParam(name = "orderNo", required = false) String orderNo,
            @Parameter(description = "开始日期，格式 yyyy-MM-dd", example = "2026-03-01") @RequestParam(name = "startDate", required = false) String startDate,
            @Parameter(description = "结束日期，格式 yyyy-MM-dd", example = "2026-03-31") @RequestParam(name = "endDate", required = false) String endDate) {
        return JsonData.buildSuccess(cashOrderService.getCashOrders(page, size, orderType, status, customerKeyword,
                orderNo, startDate, endDate));
    }

    @Operation(summary = "审核提现订单", description = "提现审核页使用。status: 1=同意, 2=拒绝",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/{id}/review")
    public JsonData<Void> reviewWithdrawOrder(@PathVariable Long id,
            @Valid @RequestBody ReviewCashOrderRequest request,
            HttpServletRequest httpRequest) {
        Long reviewerId = SecurityUtil.getCurrentUserId(httpRequest, token -> {
            AdminUser user = authService.getCurrentUser(token);
            return user != null ? user.getId() : null;
        });

        cashOrderService.reviewWithdrawOrder(id, request.getStatus(), request.getRemark(), reviewerId);
        return JsonData.buildSuccess();
    }

    @Operation(summary = "手动完成资金订单", description = "模拟第三方回调。充值订单可从 CREATED/PROCESSING 标记成功或失败；提现订单可从 PROCESSING 标记成功或失败。",
            security = @SecurityRequirement(name = "Bearer"))
    @PostMapping("/{id}/complete")
    @Hidden
    public JsonData<Void> completeCashOrder(@PathVariable Long id,
            @Valid @RequestBody CompleteCashOrderRequest request,
            HttpServletRequest httpRequest) {
        Long operatorId = SecurityUtil.getCurrentUserId(httpRequest, token -> {
            AdminUser user = authService.getCurrentUser(token);
            return user != null ? user.getId() : null;
        });

        cashOrderService.completeCashOrder(id, request.getStatus(), operatorId);
        return JsonData.buildSuccess();
    }
}
