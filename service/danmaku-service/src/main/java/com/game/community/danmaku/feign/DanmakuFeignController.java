package com.game.community.danmaku.feign;

import com.game.community.danmaku.service.DanmakuQueryService;
import com.game.community.model.base.Result;
import com.game.community.model.vo.danmaku.DanmakuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/feign/danmaku")
@RequiredArgsConstructor
public class DanmakuFeignController {

    private final DanmakuQueryService queryService;

    @GetMapping("/messages/{messageId}")
    public Result<DanmakuVO> getMessage(@PathVariable("messageId") Long messageId) {
        return Result.success(queryService.get(messageId));
    }

    @PostMapping("/messages/{messageId}/hide")
    public Result<Void> hideMessage(@PathVariable("messageId") Long messageId) {
        queryService.hide(messageId);
        return Result.success(null);
    }
}
