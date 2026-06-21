package com.game.community.utils;

import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 密码加盐加密工具
 */
public class EncryptUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateSalt() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static String md5WithSalt(String rawPassword, String salt) {
        return DigestUtils.md5DigestAsHex((rawPassword + salt).getBytes(StandardCharsets.UTF_8));
    }

    public static boolean md5Check(String rawPassword, String encodedPassword, String salt) {
        return md5WithSalt(rawPassword, salt).equals(encodedPassword);
    }

    private EncryptUtils() {
    }
}
