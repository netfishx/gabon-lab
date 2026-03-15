package com.gabon.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.stream.Collectors;

public class PaymentUtils {

    private static final String SECRET_KEY = "255bab5dda5fc7b2aa10e33bf2817a44"; // 商户密钥

    /**
     * 生成签名
     * @param params 需要签名的参数
     * @return 签名字符串
     */
    public static String generateSign(Map<String, String> params) {
        // 1️过滤空值 & 排序
        String signStr = params.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty()) // 过滤空值
                .sorted(Map.Entry.comparingByKey()) // ASCII排序
                .map(e -> e.getKey() + "=" + e.getValue()) // 拼接 key=value
                .collect(Collectors.joining("&")); // 用 & 连接

        // 2️ 追加密钥
        signStr += "&key=" + SECRET_KEY;

        // 3️ 计算 MD5
        return md5(signStr);
    }

    /**
     * 计算 MD5 哈希值
     */
    private static String md5(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5计算失败", e);
        }
    }
}
