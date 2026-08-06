package com.game.community.utils.audit;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 审核结果（合规分 0-10）
 * <p>
 * 0-3 拒绝，4-6 人工复核，7-10 通过。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditResult {

    private Integer score;

    private String reason;

    private Long durationMs;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    public AuditResult(Integer score, String reason) {
        this.score = score;
        this.reason = reason;
    }

    /** 兼容旧逻辑：score &gt;= 7 视为通过 */
    public boolean isPass() {
        return score != null && score >= 7;
    }

    public boolean isHumanReview() {
        return score != null && score >= 4 && score <= 6;
    }

    public boolean isReject() {
        return score == null || score <= 3;
    }

    public static AuditResult of(int score, String reason) {
        return new AuditResult(clamp(score), StringUtils.hasText(reason) ? reason : defaultReason(score));
    }

    public static AuditResult pass() {
        return of(9, "通过");
    }

    public static AuditResult reject(String reason) {
        return of(1, StringUtils.hasText(reason) ? reason : "审核未通过");
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

    public static AuditResult of(int score, String reason, Long durationMs, Integer promptTokens,
                                 Integer completionTokens, Integer totalTokens) {
        AuditResult result = of(score, reason);
        result.setDurationMs(durationMs);
        result.setPromptTokens(promptTokens);
        result.setCompletionTokens(completionTokens);
        result.setTotalTokens(totalTokens);
        return result;
    }

    private static int clamp(int score) {
        if (score < 0) {
            return 0;
        }
        if (score > 10) {
            return 10;
        }
        return score;
    }

    private static String defaultReason(int score) {
        if (score <= 3) {
            return "审核未通过";
        }
        if (score <= 6) {
            return "需人工复核";
        }
        return "通过";
    }
}
