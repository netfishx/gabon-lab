package com.gabon.service.model.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "钱包信息")
public class WalletVO {

    @Schema(description = "总余额（钻石）")
    private Long totalBalance;

    @Schema(description = "近7天收益（钻石，仅含任务/签到/邀请/观看奖励）")
    private Long sevenDayEarnings;

    @Schema(description = "近7天流水明细")
    private List<CustomerTransactionVO> transactions;
}
