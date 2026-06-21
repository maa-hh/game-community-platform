package com.game.community.ai.controller;

import com.game.community.ai.service.KnowledgeDocumentService;
import com.game.community.common.annotation.AdminCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.aiagent.AddKnowledgeTextRequest;
import com.game.community.model.dto.aiagent.AiKnowledgePageQuery;
import com.game.community.model.vo.aiagent.AiKnowledgeDetailVO;
import com.game.community.model.vo.aiagent.AiKnowledgeDocumentVO;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;

@RestController
@RequestMapping("/ai/knowledge")
@RequiredArgsConstructor
public class AiKnowledgeController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    @AdminCheck
    @PostMapping("/text")
    public Result<AiKnowledgeDocumentVO> addText(@Valid @RequestBody AddKnowledgeTextRequest request) {
        return Result.success(knowledgeDocumentService.addText(UserThreadLocal.getUserId(), request));
    }

    @AdminCheck
    @PostMapping("/file")
    public Result<AiKnowledgeDocumentVO> addFile(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(knowledgeDocumentService.addFile(UserThreadLocal.getUserId(), file));
    }

    @AdminCheck
    @GetMapping("/page")
    public PageResult<AiKnowledgeDocumentVO> page(@ModelAttribute AiKnowledgePageQuery query) {
        return knowledgeDocumentService.page(query);
    }

    @AdminCheck
    @GetMapping("/{documentId}")
    public Result<AiKnowledgeDetailVO> detail(@PathVariable("documentId") Long documentId) {
        return Result.success(knowledgeDocumentService.detail(documentId));
    }

    @AdminCheck
    @PutMapping("/{documentId}/disable")
    public Result<Void> disable(@PathVariable("documentId") Long documentId) {
        knowledgeDocumentService.disable(UserThreadLocal.getUserId(), documentId);
        return Result.success(null);
    }

    @AdminCheck
    @DeleteMapping("/{documentId}")
    public Result<Void> delete(@PathVariable("documentId") Long documentId) {
        knowledgeDocumentService.delete(UserThreadLocal.getUserId(), documentId);
        return Result.success(null);
    }

    @AdminCheck
    @PostMapping("/{documentId}/reindex")
    public Result<Void> reindex(@PathVariable("documentId") Long documentId) {
        knowledgeDocumentService.reindex(UserThreadLocal.getUserId(), documentId);
        return Result.success(null);
    }
}
