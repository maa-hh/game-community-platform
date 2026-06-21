package com.game.community.ai.controller;

import com.game.community.ai.service.AiChatService;
import com.game.community.ai.service.ChatMemoryService;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.Result;
import com.game.community.model.dto.aiagent.AiChatRequest;
import com.game.community.model.vo.aiagent.AiChatMessageVO;
import com.game.community.model.vo.aiagent.AiChatResponseVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;
    private final ChatMemoryService chatMemoryService;

    @LoginCheck
    @PostMapping
    public Result<AiChatResponseVO> chat(@Valid @RequestBody AiChatRequest request) {
        return Result.success(aiChatService.chat(request.getSessionId(), request.getMessage()));
    }

    @LoginCheck
    @GetMapping("/session/{sessionId}/messages")
    public Result<List<AiChatMessageVO>> messages(@PathVariable("sessionId") String sessionId) {
        return Result.success(chatMemoryService.getMessages(sessionId));
    }

    @LoginCheck
    @DeleteMapping("/session/{sessionId}")
    public Result<Void> clear(@PathVariable("sessionId") String sessionId) {
        chatMemoryService.clear(sessionId);
        return Result.success(null);
    }
}
