package com.game.community.common.constant.email;

/** 邮件主题和正文模板。 */
public final class EmailConstants {

    public static final String SUBJECT_REGISTER = "游戏社区 - 注册验证码";
    public static final String SUBJECT_RESET_PASSWORD = "游戏社区 - 找回密码验证码";
    public static final String SUBJECT_CHANGE_EMAIL = "游戏社区 - 修改邮箱验证码";
    public static final String SUBJECT_CANCEL_ACCOUNT = "游戏社区 - 注销账号验证码";

    public static final String BODY_PREFIX = "您的验证码是：";
    public static final String BODY_CODE_SEPARATOR = "，";
    public static final String BODY_SUFFIX = "。如非本人操作请忽略。";
    public static final String EXPIRE_MINUTE_SUFFIX = " 分钟内有效";
    public static final String EXPIRE_MINUTE_SECOND_SEPARATOR = " 分 ";
    public static final String EXPIRE_SECOND_SUFFIX = " 秒内有效";

    private EmailConstants() {
    }
}
