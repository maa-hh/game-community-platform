package com.game.community.utils.audit;

/**
 * 内容审核客户端
 */
public interface AuditClient {

    AuditResult auditText(String text);

    AuditResult auditImage(byte[] imageBytes, String mimeType);

    AuditResult auditImageUrl(String imageUrl);
}
