package com.game.community.game.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.game.service.GameResourceAdminService;
import com.game.community.game.service.GameSignInService;
import com.game.community.model.base.Result;
import com.game.community.model.entity.gameaccount.SignInReward;
import com.game.community.model.vo.gameaccount.SignInResultVO;
import com.game.community.model.vo.gameaccount.SignInStatusVO;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/game-account/sign-in")
@RequiredArgsConstructor
public class GameSignInController {

    private final GameSignInService gameSignInService;
    private final GameResourceAdminService gameResourceAdminService;

    @LoginCheck
    @PostMapping
    public Result<SignInResultVO> signIn() {
        return Result.success(gameSignInService.signIn(UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @GetMapping("/status")
    public Result<SignInStatusVO> status() {
        return Result.success(gameSignInService.getStatus(UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @GetMapping("/rewards")
    public Result<List<SignInReward>> rewards() {
        return Result.success(gameResourceAdminService.listSignInRewards());
    }
}
