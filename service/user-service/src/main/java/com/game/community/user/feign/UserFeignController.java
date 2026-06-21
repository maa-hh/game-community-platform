package com.game.community.user.feign;

import com.game.community.model.base.Result;
import com.game.community.model.vo.user.UserVO;
import com.game.community.user.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/feign/user")
@RequiredArgsConstructor
public class UserFeignController {

    private final IUserService userService;

    @GetMapping("/ids")
    public Result<List<UserVO>> getUsersByIds(@RequestParam("ids") List<Long> ids) {
        return Result.success(userService.getUsersByIds(ids));
    }

    @PostMapping("/{userId}/status")
    public Result<Void> updateUserStatus(@PathVariable("userId") Long userId,
                                         @RequestParam("status") Integer status) {
        userService.switchUserStatus(userId, status);
        return Result.success(null);
    }
}
