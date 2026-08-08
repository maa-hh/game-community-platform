package com.game.community.model.vo.user;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前登录用户资料（仅 /user/me）
 */
@Data
public class UserMeVO {

    /** 对外账号 ID；t_user.id 仅在服务内部使用。 */
    private Long accountId;

    /** 用户资料乐观锁版本。 */
    private Integer version;

    private String username;

    private String avatar;

    private String signature;

    private String email;

    private String steamAccount;

    /** 账号状态：0正常 1封禁 2注销中 3已注销 */
    private Integer status;

    /** 账号类型：0普通 1管理员 */
    private Integer type;

    private String pendingUsername;

    private String pendingSignature;

    private String pendingAvatarUrl;

    private Integer usernameAuditStatus;

    private Integer signatureAuditStatus;

    private Integer avatarAuditStatus;

    private String usernameAuditMessage;

    private String signatureAuditMessage;

    private String avatarAuditMessage;

    private Integer followCount;

    private Integer fansCount;

    private LocalDateTime banUntil;

    private String banReason;
}
