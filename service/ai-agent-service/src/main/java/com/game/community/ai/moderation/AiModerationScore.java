package com.game.community.ai.moderation;

import lombok.Data;

/** Spring AI 结构化输出目标。 */
@Data
public class AiModerationScore {

    private Integer score;

    private String reason;
}
