package com.game.community.ai.feign;

import com.game.community.ai.service.AiCapabilityService;
import com.game.community.model.base.Result;
import com.game.community.model.dto.aiagent.AiEmbeddingRequest;
import com.game.community.model.dto.aiagent.AiSearchTermsRequest;
import com.game.community.model.vo.aiagent.AiEmbeddingResponseVO;
import com.game.community.model.vo.aiagent.AiSearchTermsResponseVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 面向内部微服务的无状态 AI 能力接口。 */
@RestController
@RequestMapping("/feign/ai")
@RequiredArgsConstructor
public class AiCapabilityFeignController {

    private final AiCapabilityService capabilityService;

    @PostMapping("/embedding")
    public Result<AiEmbeddingResponseVO> embedding(@Valid @RequestBody AiEmbeddingRequest request) {
        return Result.success(capabilityService.embedding(request));
    }

    @PostMapping("/search-terms")
    public Result<AiSearchTermsResponseVO> expandSearchTerms(
            @Valid @RequestBody AiSearchTermsRequest request) {
        return Result.success(capabilityService.expandSearchTerms(request));
    }
}
