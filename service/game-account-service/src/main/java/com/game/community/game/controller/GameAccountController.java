package com.game.community.game.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.game.service.GameAccountBindingService;
import com.game.community.model.base.Result;
import com.game.community.model.dto.gameaccount.GameAccountBindDTO;
import com.game.community.model.vo.gameaccount.GameAccountBindVO;
import com.game.community.model.vo.gameaccount.GameAccountProfileVO;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/game-account")
@RequiredArgsConstructor
public class GameAccountController {

    private final GameAccountBindingService bindingService;

    @LoginCheck
    @PostMapping("/bind")
    public Result<GameAccountBindVO> bind(@Valid @RequestBody GameAccountBindDTO dto) {
        return Result.success(bindingService.bind(UserThreadLocal.getUserId(), dto));
    }

    @LoginCheck
    @PutMapping("/rebind")
    public Result<GameAccountBindVO> rebind(@Valid @RequestBody GameAccountBindDTO dto) {
        return Result.success(bindingService.rebind(UserThreadLocal.getUserId(), dto));
    }

    @LoginCheck
    @DeleteMapping("/unbind")
    public Result<Void> unbind() {
        bindingService.unbind(UserThreadLocal.getUserId());
        return Result.success(null);
    }

    @LoginCheck
    @GetMapping("/me")
    public Result<GameAccountProfileVO> current() {
        return Result.success(bindingService.current(UserThreadLocal.getUserId()));
    }
}
