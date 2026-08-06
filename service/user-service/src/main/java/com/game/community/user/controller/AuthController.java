package com.game.community.user.controller;

import com.game.community.common.constant.Constants;
import com.game.community.model.base.Result;
import com.game.community.model.dto.user.AccountLoginDTO;
import com.game.community.model.dto.user.RegisterDTO;
import com.game.community.model.dto.user.ResetPasswordDTO;
import com.game.community.model.dto.user.SendCodeDTO;
import com.game.community.model.vo.user.LoginVO;
import com.game.community.model.vo.user.RegisterVO;
import com.game.community.model.vo.user.SendCodeVO;
import com.game.community.model.vo.user.TokenRefreshVO;
import com.game.community.user.service.UserAuthService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import com.game.community.utils.web.ClientInfo;
import com.game.community.utils.web.CookieHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 HTTP 入口（/user/auth/*，网关白名单，无需 access JWT）。
 * <p>
 * 本类只做两件事：把 request/Cookie 转成 Service 入参；把 Service 返回的 refresh 写入 HttpOnly Cookie。
 */
@RestController
@RequestMapping("/user/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserAuthService userAuthService;
    private final CookieHelper cookieHelper;

    /** 入参：email + bizType(REGISTER|RESET_PASSWORD)；出参：expireIn(秒) */
    @PostMapping("/send-code")
    public Result<SendCodeVO> sendCode(@Valid @RequestBody SendCodeDTO dto) {
        return userAuthService.sendCode(dto);
    }

    /** 入参：email + password + code；出参：email；不发 token */
    @PostMapping("/register")
    public Result<RegisterVO> register(@Valid @RequestBody RegisterDTO dto) {
        return userAuthService.register(dto);
    }

    /** 入参：email + password + code；成功后清 refresh Cookie */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordDTO dto,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        Result<Void> result = userAuthService.resetPassword(dto);
        cookieHelper.clearRefreshTokenCookie(request, response);
        return result;
    }

    /**
     * 入参：email + password；出参 JSON：accessToken + user；
     * refresh 在 LoginVO.refreshToken（@JsonIgnore），由此处写 Cookie。
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody AccountLoginDTO dto,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        Result<LoginVO> result = userAuthService.login(dto, ClientInfo.from(request));
        LoginVO loginVO = result.getData();
        if (loginVO != null && StringUtils.hasText(loginVO.getRefreshToken())) {
            cookieHelper.writeRefreshTokenCookie(request, response,
                    loginVO.getRefreshToken(), Constants.REFRESH_TOKEN_EXPIRE_TIME);
        }
        return result;
    }

    /**
     * 入参：refresh 从 Cookie 读取；出参 JSON：accessToken；
     * 新 refresh 写回 Cookie。
     */
    @PostMapping("/refresh")
    public Result<TokenRefreshVO> refreshToken(HttpServletRequest request,
                                               HttpServletResponse response) {
        String refreshToken = cookieHelper.extractRefreshToken(request);
        Result<TokenRefreshVO> result = userAuthService.refreshToken(refreshToken);
        TokenRefreshVO vo = result.getData();
        if (vo != null && StringUtils.hasText(vo.getRefreshToken())) {
            cookieHelper.writeRefreshTokenCookie(request, response,
                    vo.getRefreshToken(), Constants.REFRESH_TOKEN_EXPIRE_TIME);
        }
        return result;
    }

    /**
     * 入参：userId/sessionId 来自 ThreadLocal（有 access 时）；
     * refresh 来自 Cookie（access 过期时）；成功后清 Cookie + ThreadLocal。
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Result<Void> result = userAuthService.logout(
                UserThreadLocal.getUserId(),
                UserThreadLocal.getSessionId(),
                cookieHelper.extractRefreshToken(request));
        cookieHelper.clearRefreshTokenCookie(request, response);
        UserThreadLocal.removeUser();
        return result;
    }
}
