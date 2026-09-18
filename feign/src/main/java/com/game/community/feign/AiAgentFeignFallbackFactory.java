package com.game.community.feign;

import com.game.community.model.base.Result;
import com.game.community.model.dto.aiagent.AiEmbeddingRequest;
import com.game.community.model.dto.aiagent.AiSearchTermsRequest;
import com.game.community.model.dto.aiagent.ModerationRequest;
import com.game.community.model.vo.aiagent.AiEmbeddingResponseVO;
import com.game.community.model.vo.aiagent.AiSearchTermsResponseVO;
import com.game.community.model.enums.aiagent.ModerationCheckResult;
import com.game.community.model.enums.aiagent.ModerationContentType;
import com.game.community.model.enums.aiagent.ModerationDecision;
import com.game.community.model.vo.aiagent.ModerationResultVO;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/** AI Agent Feign 熔断降级工厂，远程不可用时统一进入人工审核。 */
@Component
public class AiAgentFeignFallbackFactory implements FallbackFactory<AiAgentFeignClient> {

    /** 为每次远程失败创建不放行的人工审核降级结果。 */
    @Override
    public AiAgentFeignClient create(Throwable cause) {
        return new AiAgentFeignClient() {
            @Override
            public Result<ModerationResultVO> moderate(ModerationRequest request) {
                return Result.success("AI 服务熔断，已转人工审核", unavailable(request));
            }

            @Override
            public Result<AiEmbeddingResponseVO> embedding(AiEmbeddingRequest request) {
                return Result.error(503, "AI embedding 服务暂不可用");
            }

            @Override
            public Result<AiSearchTermsResponseVO> expandSearchTerms(AiSearchTermsRequest request) {
                return Result.error(503, "AI 搜索词服务暂不可用");
            }
        };
    }

    /** 构造跨服务失败时的审核结果，禁止误放行内容。 */
    private ModerationResultVO unavailable(ModerationRequest request) {
        ModerationContentType type = request == null ? null : request.getType();
        ModerationResultVO result = new ModerationResultVO();
        result.setType(type);
        result.setKeywordAudit(type == ModerationContentType.TEXT || type == ModerationContentType.ARTICLE
                ? ModerationCheckResult.PASS : ModerationCheckResult.NOT_APPLICABLE);
        result.setMatchedKeywords(java.util.List.of());
        result.setAiAudit(ModerationCheckResult.NOT_EXECUTED);
        result.setScore(5);
        result.setReason("AI审核服务熔断，转人工审核");
        result.setResult(ModerationDecision.HUMAN_REVIEW);
        return result;
    }
}
