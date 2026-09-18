package com.game.community.ai.context;

import com.game.community.model.ThreadLocal.UserContex;

/** AI 服务请求级用户上下文，确保线程复用时不会串用户信息。 */
public final class AiUserContextHolder {

    private static final ThreadLocal<UserContex> CONTEXT = new ThreadLocal<>();

    private AiUserContextHolder() {
    }

    /** 写入当前请求的用户上下文。 */
    public static void set(UserContex context) {
        CONTEXT.set(context);
    }

    /** 获取当前请求的用户上下文。 */
    public static UserContex get() {
        return CONTEXT.get();
    }

    /** 获取当前请求用户 ID。 */
    public static Long getUserId() {
        UserContex context = CONTEXT.get();
        return context == null ? null : context.getUserId();
    }

    /** 获取当前请求用户类型。 */
    public static Integer getType() {
        UserContex context = CONTEXT.get();
        return context == null ? null : context.getType();
    }

    /** 清理当前线程上下文，避免线程池复用造成数据泄漏。 */
    public static void clear() {
        CONTEXT.remove();
    }
}
