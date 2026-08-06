package com.game.community.model.enums.user;

/**
 * 用户模块字符串字段默认值（避免向库表写入 null）
 */
public final class UserStrings {

    public static final String EMPTY = "";

    private UserStrings() {
    }

    public static String orEmpty(String value) {
        return value == null ? EMPTY : value;
    }
}
