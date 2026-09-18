package com.game.community.feign;

import com.game.community.model.base.Result;
import com.game.community.model.dto.aiagent.AiEmbeddingRequest;
import com.game.community.model.dto.aiagent.AiSearchTermsRequest;
import com.game.community.model.dto.aiagent.ModerationRequest;
import com.game.community.model.vo.aiagent.AiEmbeddingResponseVO;
import com.game.community.model.vo.aiagent.AiSearchTermsResponseVO;
import com.game.community.model.vo.aiagent.ModerationResultVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** AI Agent 服务内部调用协议。 */
@FeignClient(name = "ai-agent-service", contextId = "aiAgentFeignClient",
        path = "/feign/ai", fallbackFactory = AiAgentFeignFallbackFactory.class)
public interface AiAgentFeignClient {

    /** 执行文本或图片审核并返回统一决策。 */
    @PostMapping("/moderation")
    Result<ModerationResultVO> moderate(@RequestBody ModerationRequest request);

    @PostMapping("/embedding")
    Result<AiEmbeddingResponseVO> embedding(@RequestBody AiEmbeddingRequest request);

    @PostMapping("/search-terms")
    Result<AiSearchTermsResponseVO> expandSearchTerms(@RequestBody AiSearchTermsRequest request);
}
