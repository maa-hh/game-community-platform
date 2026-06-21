package com.game.community.utils.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审核结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditResult {

    private boolean pass;

    private String reason;

    private Long durationMs;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    public AuditResult(boolean pass, String reason) {
        this.pass = pass;
        this.reason = reason;
    }

    public static AuditResult pass() {
        return new AuditResult(true, "通过");
    }

    public static AuditResult reject(String reason) {
        return new AuditResult(false, reason);
    }

    public static AuditResult pass(Long durationMs, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        AuditResult result = pass();
        result.setDurationMs(durationMs);
        result.setPromptTokens(promptTokens);
        result.setCompletionTokens(completionTokens);
        result.setTotalTokens(totalTokens);
        return result;
    }

    public static AuditResult reject(String reason, Long durationMs, Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        AuditResult result = reject(reason);
        result.setDurationMs(durationMs);
        result.setPromptTokens(promptTokens);
        result.setCompletionTokens(completionTokens);
        result.setTotalTokens(totalTokens);
        return result;
    }
}
