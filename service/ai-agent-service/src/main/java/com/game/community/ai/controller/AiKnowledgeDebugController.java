package com.game.community.ai.controller;

import com.game.community.ai.service.KnowledgeIndexService;
import com.game.community.ai.service.KnowledgeRetrievalService;
import com.game.community.common.annotation.AdminCheck;
import com.game.community.model.base.Result;
import com.game.community.model.dto.aiagent.KnowledgeSearchDebugRequest;
import com.game.community.model.vo.aiagent.AiKnowledgeDebugVO;
import com.game.community.model.vo.aiagent.AiKnowledgeStatsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/knowledge/debug")
@RequiredArgsConstructor
public class AiKnowledgeDebugController {

    private final KnowledgeRetrievalService retrievalService;
    private final KnowledgeIndexService knowledgeIndexService;

    @AdminCheck
    @PostMapping("/search")
    public Result<AiKnowledgeDebugVO> search(@Valid @RequestBody KnowledgeSearchDebugRequest request) {
        return Result.success(retrievalService.debugSearch(request.getQuery(), request.getTopK()));
    }

    @AdminCheck
    @GetMapping("/stats")
    public Result<AiKnowledgeStatsVO> stats() {
        return Result.success(knowledgeIndexService.stats());
    }
}
