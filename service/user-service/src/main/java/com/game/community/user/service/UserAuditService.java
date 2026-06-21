package com.game.community.user.service;

/**
 * 用户资料审核服务
 */
public interface UserAuditService {

    boolean auditUserInfo(String nickname, String signature);

    boolean auditAvatarUrl(String avatarUrl);
}
