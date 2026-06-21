package com.game.community.feign;

import com.game.community.model.base.Result;
import com.game.community.model.vo.user.UserVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "user-service", contextId = "userFeignClient", path = "/feign/user")
public interface UserFeignClient {

    @GetMapping("/ids")
    Result<List<UserVO>> getUsersByIds(@RequestParam("ids") List<Long> ids);

    @PostMapping("/{userId}/status")
    Result<Void> updateUserStatus(@PathVariable("userId") Long userId,
                                  @RequestParam("status") Integer status);
}
