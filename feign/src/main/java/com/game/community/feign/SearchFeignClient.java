package com.game.community.feign;

import com.game.community.model.base.Result;
import com.game.community.model.vo.game.GameTagVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/** 内容服务读取游戏标签的轻量搜索接口。 */
@FeignClient(name = "search-service", contextId = "searchGameFeignClient", path = "/feign/search")
public interface SearchFeignClient {

    @PostMapping("/games/tags")
    Result<List<GameTagVO>> listGameTags(@RequestBody List<Long> appIds);
}
