package com.gabon.service.model.vo;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "钱包信息")
public class WalletVO {

    @Schema(description = "总余额（钻石）")
    private Long totalBalance;

    @Schema(description = "冻结余额（钻石）")
    private Long frozenBalance;

    @Schema(description = "可用余额（钻石）")
    private Long spendableBalance;

    @Schema(description = "当前可提现金额（CNY）")
    private BigDecimal withdrawableAmountCny;

    @Schema(description = "当前汇率（每1 CNY对应钻石数）")
    private BigDecimal exchangeRate;

    @Schema(description = "法币币种", example = "CNY")
    private String currencyCode;

    @Schema(description = "近7天收益（钻石，仅含任务/签到/邀请/观看奖励）")
    private Long sevenDayEarnings;

    @Schema(description = "近7天流水明细")
    private List<CustomerTransactionVO> transactions;
}
