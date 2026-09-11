package com.game.community.model.enums.aiagent;

/** 单个审核阶段的执行结果。 */
public enum ModerationCheckResult {
    PASS,
    REJECT,
    HUMAN_REVIEW,
    NOT_EXECUTED,
    NOT_APPLICABLE
}
