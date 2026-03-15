package com.gabon.common.enums;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum RechargeAmountEnum {

    RMB_30(30, 35),
    RMB_50(50, 69),
    RMB_100(100, 149),
    RMB_200(200, 319),
    RMB_300(300, 499),
    RMB_500(500, 799);

    private final Integer amount;   // 充值金额（元）
    private final Integer coins;    // 获得的金币数

        RechargeAmountEnum( Integer amount, Integer coins) {
        this.amount = amount;
        this.coins = coins;
    }

    /**
     * 根据充值金额获取对应的金币数
     */
    public static int getCoinsByAmount(Integer amount) {
        for (RechargeAmountEnum item : values()) {
            if (item.getAmount().equals( amount)) {
                return item.getCoins();
            }
        }
        throw new IllegalArgumentException("无效的充值金额: " + amount);
    }

    /**
     * 获取所有充值金额和对应金币的映射
     */
    public static Map<Integer, Integer> getAllRechargeOptions() {
        Map<Integer, Integer> rechargeMap = new HashMap<>();
        for (RechargeAmountEnum item : values()) {
            rechargeMap.put(item.getAmount(), item.getCoins());
        }
        return rechargeMap;
    }
}
