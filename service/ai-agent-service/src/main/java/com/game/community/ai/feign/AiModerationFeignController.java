package com.game.community.ai.feign;

import com.game.community.ai.service.AiModerationService;
import com.game.community.model.base.Result;
import com.game.community.model.dto.aiagent.ModerationRequest;
import com.game.community.model.vo.aiagent.ModerationResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 微服务内部 AI 审核入口。 */
@RestController
@RequestMapping("/feign/ai")
@RequiredArgsConstructor
public class AiModerationFeignController {

    private final AiModerationService moderationService;

    /** 转发统一审核命令，具体规则由 Service 负责。 */
    @PostMapping("/moderation")
    public Result<ModerationResultVO> moderate(@Valid @RequestBody ModerationRequest request) {
        return Result.success(moderationService.moderate(request));
    }
}
