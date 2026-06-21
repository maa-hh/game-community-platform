package com.game.community.user.service.impl;

import com.game.community.user.service.UserAuditService;
import com.game.community.utils.DfaAuditUtils;
import com.game.community.utils.MinIOUtils;
import com.game.community.utils.audit.AuditClient;
import com.game.community.utils.audit.AuditResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 用户资料审核实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuditServiceImpl implements UserAuditService {

    private final DfaAuditUtils dfaAuditUtils;

    private final AuditClient auditClient;

    private final MinIOUtils minIOUtils;

    @Override
    public boolean auditUserInfo(String nickname, String signature) {
        return auditText(nickname, "昵称") && auditText(signature, "个性签名");
    }

    @Override
    public boolean auditAvatarUrl(String avatarUrl) {
        AuditResult result;
        try {
            MinIOUtils.FilePayload filePayload = minIOUtils.readFileByUrl(avatarUrl);
            result = auditClient.auditImage(filePayload.bytes(), filePayload.contentType());
        } catch (Exception e) {
            log.warn("头像图片读取失败，回退为 URL 审核: url={}, error={}", avatarUrl, e.getMessage());
            result = auditClient.auditImageUrl(avatarUrl);
        }
        if (!result.isPass()) {
            log.warn("头像URL审核未通过: reason={}, url={}", result.getReason(), avatarUrl);
            return false;
        }
        return true;
    }

    private boolean auditText(String text, String fieldName) {
        if (!StringUtils.hasText(text)) {
            return true;
        }
        if (!dfaAuditUtils.pass(text)) {
            log.warn("{}本地审核未通过: {}", fieldName, text);
            return false;
        }
        AuditResult result = auditClient.auditText(text);
        if (!result.isPass()) {
            log.warn("{}AI审核未通过: {}", fieldName, result.getReason());
            return false;
        }
        return true;
    }
}
