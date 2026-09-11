package com.game.community.ai.service;

import com.game.community.model.dto.aiagent.ModerationRequest;
import com.game.community.model.vo.aiagent.ModerationResultVO;

/** AI Agent 统一内容审核边界。 */
public interface AiModerationService {

    /** 先执行本地规则，再按内容类型调用对应 AI 模型。 */
    ModerationResultVO moderate(ModerationRequest request);
}
