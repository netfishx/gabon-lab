package com.gabon.common.util;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码工具类
 */
public class PasswordUtil {

    private static final String SALT_PREFIX = "gabon_admin_";
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成盐值
     * 
     * @return 盐值
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        return SALT_PREFIX + Base64.getEncoder().encodeToString(salt);
    }

    /**
     * 加密密码
     * 
     * @param plainPassword 明文密码
     * @param salt          盐值
     * @return 加密后的密码
     */
    public static String encryptPassword(String plainPassword, String salt) {
        if (!StringUtils.hasText(plainPassword) || !StringUtils.hasText(salt)) {
            throw new IllegalArgumentException("密码和盐值不能为空");
        }
        return DigestUtils.sha256Hex(salt + plainPassword);
    }

    /**
     * 加密密码（自动生成盐值）
     * 
     * @param plainPassword 明文密码
     * @return 格式为 "salt$encryptedPassword" 的字符串
     */
    public static String encryptPassword(String plainPassword) {
        String salt = generateSalt();
        String encrypted = encryptPassword(plainPassword, salt);
        return salt + "$" + encrypted;
    }

    /**
     * 验证密码
     * 
     * @param plainPassword     明文密码
     * @param encryptedPassword 加密后的密码（格式：salt$encryptedPassword）
     * @return 是否匹配
     */
    public static boolean verifyPassword(String plainPassword, String encryptedPassword) {
        if (!StringUtils.hasText(plainPassword) || !StringUtils.hasText(encryptedPassword)) {
            return false;
        }

        String[] parts = encryptedPassword.split("\\$", 2);
        if (parts.length != 2) {
            return false;
        }

        String salt = parts[0];
        String encrypted = parts[1];
        String newEncrypted = encryptPassword(plainPassword, salt);

        return encrypted.equals(newEncrypted);
    }

    /**
     * 验证密码强度
     * 
     * @param password 密码
     * @return 是否符合强度要求
     */
    public static boolean isStrongPassword(String password) {
        if (!StringUtils.hasText(password) || password.length() < 6) {
            return false;
        }

        // 至少包含字母和数字
        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        boolean hasDigit = password.matches(".*\\d.*");

        return hasLetter && hasDigit;
    }

    /**
     * 生成随机密码
     * 
     * @param length 密码长度
     * @return 随机密码
     */
    public static String generateRandomPassword(int length) {
        if (length < 6) {
            length = 6;
        }

        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder password = new StringBuilder();

        for (int i = 0; i < length; i++) {
            password.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }

        return password.toString();
    }
}
