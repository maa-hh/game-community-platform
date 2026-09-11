package com.game.community.model.vo.aiagent;

import com.game.community.model.enums.aiagent.ModerationCheckResult;
import com.game.community.model.enums.aiagent.ModerationContentType;
import com.game.community.model.enums.aiagent.ModerationDecision;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 文本和图片共用的完整审核结果。 */
@Data
public class ModerationResultVO implements Serializable {

    private ModerationContentType type;

    private ModerationCheckResult keywordAudit;

    private List<String> matchedKeywords;

    private ModerationCheckResult aiAudit;

    private Integer score;

    private String reason;

    private ModerationDecision result;

    private String provider;

    private String model;

    private Long durationMs;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;
}
