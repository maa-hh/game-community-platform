package com.game.community.utils.ThreadLocal;

import com.game.community.model.ThreadLocal.UserContex;

/**
 * 用户上下文 ThreadLocal 工具
 */
public class UserThreadLocal {

    private static final ThreadLocal<UserContex> USER_CONTEXT = new ThreadLocal<>();

    public static void setUser(UserContex user) {
        USER_CONTEXT.set(user);
    }

    public static UserContex getUser() {
        return USER_CONTEXT.get();
    }

    public static Long getUserId() {
        UserContex context = USER_CONTEXT.get();
        return context == null ? null : context.getUserId();
    }

    public static Integer getType() {
        UserContex context = USER_CONTEXT.get();
        return context == null ? null : context.getType();
    }

    public static String getSteamAccount() {
        UserContex context = USER_CONTEXT.get();
        return context == null ? null : context.getSteamAccount();
    }

    public static String getSessionId() {
        UserContex context = USER_CONTEXT.get();
        return context == null ? null : context.getSessionId();
    }

    public static void removeUser() {
        USER_CONTEXT.remove();
    }

    private UserThreadLocal() {
    }
}
