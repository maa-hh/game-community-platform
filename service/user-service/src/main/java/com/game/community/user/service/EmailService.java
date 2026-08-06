package com.game.community.user.service;

import com.game.community.model.enums.user.CodeBizType;

/**
 * 邮箱验证码发送
 */
public interface EmailService {

    /**
     * @param email   收件邮箱
     * @param code    6 位验证码
     * @param bizType 业务类型
     */
    void sendVerificationCode(String email, String code, CodeBizType bizType);

    /**
     * 是否对该邮箱使用固定验证码（开发 mock）
     */
    boolean useFixedCode(String email);

    /** mock 模式下使用的固定验证码 */
    String getMockFixedCode();
}
