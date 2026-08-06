package com.game.community.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密工具（BCrypt）
 */
public class EncryptUtils {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);

    public static String bcryptEncode(String rawPassword) {
        return BCRYPT.encode(rawPassword);
    }

    public static boolean bcryptCheck(String rawPassword, String encodedPassword) {
        return encodedPassword != null && BCRYPT.matches(rawPassword, encodedPassword);
    }

    private EncryptUtils() {
    }
}
