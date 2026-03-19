package com.gabon.service.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 钱包配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "wallet")
public class WalletProperties {

    /**
     * 每 1 CNY 对应的钻石数
     */
    private BigDecimal exchangeRate = new BigDecimal("100");

    /**
     * 法币币种
     */
    private String currencyCode = "CNY";
}
