package com.game.community.feign;

import com.game.community.model.base.Result;
import com.game.community.model.vo.danmaku.DanmakuVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "danmaku-service", contextId = "danmakuFeignClient", path = "/feign/danmaku")
public interface DanmakuFeignClient {

    @GetMapping("/messages/{messageId}")
    Result<DanmakuVO> getMessage(@PathVariable("messageId") Long messageId);

    @PostMapping("/messages/{messageId}/hide")
    Result<Void> hideMessage(@PathVariable("messageId") Long messageId);
}
