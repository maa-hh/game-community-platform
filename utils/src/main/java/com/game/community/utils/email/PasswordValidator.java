package com.game.community.utils.email;

/**
 * 密码规则：6-10 位，数字/字母/常见特殊字符（与前端 validate.ts 一致）
 */
public final class PasswordValidator {

    private static final String ALLOWED_SPECIAL = "!@#$%^&*()_+-=[]{};':\"\\|,.<>/?`~";

    private PasswordValidator() {
    }

    public static boolean isValid(String password) {
        if (password == null || password.length() < 6 || password.length() > 10) {
            return false;
        }
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                continue;
            }
            if (ALLOWED_SPECIAL.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    public static void assertValid(String password) {
        if (!isValid(password)) {
            throw new IllegalArgumentException("密码为 6-10 位数字、字母或特殊字符");
        }
    }
}
