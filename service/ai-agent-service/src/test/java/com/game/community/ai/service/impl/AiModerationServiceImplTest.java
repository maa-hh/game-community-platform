package com.game.community.ai.service.impl;

import com.game.community.ai.agent.AgentModelRegistry;
import com.game.community.ai.config.ModerationProperties;
import com.game.community.ai.moderation.AhoCorasickSensitiveWordMatcher;
import com.game.community.model.dto.aiagent.ModerationRequest;
import com.game.community.model.enums.aiagent.ModerationCheckResult;
import com.game.community.model.enums.aiagent.ModerationContentType;
import com.game.community.model.enums.aiagent.ModerationDecision;
import com.game.community.model.vo.aiagent.ModerationResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiModerationServiceImplTest {

    private AgentModelRegistry modelRegistry;
    private AiModerationServiceImpl service;

    /** 使用真实词库和提示词、模拟模型注册表初始化审核服务。 */
    @BeforeEach
    void setUp() throws IOException {
        ModerationProperties properties = new ModerationProperties();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
        modelRegistry = mock(AgentModelRegistry.class);
        service = new AiModerationServiceImpl(modelRegistry, properties,
                new AhoCorasickSensitiveWordMatcher(properties, resourceLoader), resourceLoader);
    }

    /** 屏蔽词直接拒绝，不应调用大模型。 */
    @Test
    void rejectsKeywordBeforeAiCall() {
        ModerationRequest request = new ModerationRequest();
        request.setType(ModerationContentType.TEXT);
        request.setContent("提供游戏外挂代理");

        ModerationResultVO result = service.moderate(request);

        assertEquals(ModerationCheckResult.REJECT, result.getKeywordAudit());
        assertEquals(ModerationCheckResult.NOT_EXECUTED, result.getAiAudit());
        assertEquals(ModerationDecision.REJECT, result.getResult());
        verify(modelRegistry, never()).textModeration(any());
    }

    /** 模型配置或调用异常默认进入人工审核。 */
    @Test
    void sendsUnavailableAiToHumanReview() {
        when(modelRegistry.textModeration(any())).thenThrow(new IllegalStateException("unavailable"));
        ModerationRequest request = new ModerationRequest();
        request.setType(ModerationContentType.TEXT);
        request.setContent("正常的游戏攻略分享");

        ModerationResultVO result = service.moderate(request);

        assertEquals(ModerationCheckResult.PASS, result.getKeywordAudit());
        assertEquals(ModerationCheckResult.NOT_EXECUTED, result.getAiAudit());
        assertEquals(5, result.getScore());
        assertEquals(ModerationDecision.HUMAN_REVIEW, result.getResult());
    }
}
