package com.game.community.user.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.Result;
import com.game.community.model.dto.user.BanUserDTO;
import com.game.community.model.dto.user.CancelAccountDTO;
import com.game.community.model.vo.user.SendCodeVO;
import com.game.community.user.service.UserAccountService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import com.game.community.utils.web.CookieHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户账户生命周期控制器：注销、撤销注销、封禁、解封
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserAccountService userAccountService;
    private final CookieHelper cookieHelper;

    /** 注销前：向当前绑定邮箱发送验证码 */
    @LoginCheck
    @PostMapping("/cancel/send-code")
    public Result<SendCodeVO> sendCancelAccountCode() {
        return userAccountService.sendCancelAccountCode();
    }

    /** 申请注销账号（本人操作，邮箱验证码通过后进入7天冷静期） */
    @LoginCheck
    @PostMapping("/cancel")
    public Result<Void> cancelAccount(@Valid @RequestBody CancelAccountDTO dto,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        Result<Void> result = userAccountService.cancelAccount(dto);
        cookieHelper.clearRefreshTokenCookie(request, response);
        UserThreadLocal.removeUser();
        return result;
    }

    /** 撤销注销（冷静期内登录后调用） */
    @LoginCheck
    @PostMapping("/cancel/revoke")
    public Result<Void> revokeCancel() {
        return userAccountService.revokeCancel();
    }

    /** 封禁用户（仅管理员） */
    @AdminCheck
    @PostMapping("/{userId}/ban")
    public Result<Void> banUser(@PathVariable("userId") Long userId,
                                @Valid @RequestBody BanUserDTO dto) {
        return userAccountService.banUser(userId, dto.getReason(), dto.getDurationHours(), null);
    }

    /** 解封用户（仅管理员） */
    @AdminCheck
    @PostMapping("/{userId}/unban")
    public Result<Void> unbanUser(@PathVariable("userId") Long userId) {
        return userAccountService.unbanUser(userId, null);
    }
}
