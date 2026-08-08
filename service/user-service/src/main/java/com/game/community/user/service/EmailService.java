package com.game.community.user.service;

import com.game.community.model.enums.user.CodeBizType;

/**
 * 邮箱验证码发送
 */
public interface EmailService {

    /**
     * 提交验证码邮件发送任务后立即返回；发送失败只记录日志，不自动重试。
     *
     * @param email   已规范化的收件邮箱；邮箱格式由验证码入口统一处理
     * @param code    6 位验证码
     * @param bizType 业务类型
     */
    void sendVerificationCode(String email, String code, CodeBizType bizType);

    /** 是否对已规范化的邮箱使用固定验证码（开发 mock） */
    boolean useFixedCode(String email);

    /** mock 模式下使用的固定验证码 */
    String getMockFixedCode();
}
