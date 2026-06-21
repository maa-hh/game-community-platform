package com.game.community.user.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.common.constant.Constants;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.user.AccountLoginDTO;
import com.game.community.model.dto.user.ChangePasswordDTO;
import com.game.community.model.dto.user.RegisterDTO;
import com.game.community.model.dto.user.SendCodeDTO;
import com.game.community.model.dto.user.UpdateUserInfoDTO;
import com.game.community.model.dto.user.UserSearchPageDTO;
import com.game.community.model.dto.user.UserStatusDTO;
import com.game.community.model.vo.user.LoginVO;
import com.game.community.model.vo.user.TokenRefreshVO;
import com.game.community.model.vo.user.UserSimpleVO;
import com.game.community.model.vo.user.UserVO;
import com.game.community.user.service.IUserService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    @PostMapping("/sendCode")
    public Result<String> sendCode(@Valid @RequestBody SendCodeDTO dto) {
        return Result.success(userService.sendCode(dto.getPhone()));
    }

    @PostMapping("/register/phone")
    public Result<Long> registerByPhone(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(userService.registerByPhone(dto));
    }

    @PostMapping("/login/account")
    public Result<LoginVO> loginByAccount(@Valid @RequestBody AccountLoginDTO dto, HttpServletRequest request,
                                          HttpServletResponse response) {
        LoginVO loginVO = userService.loginByAccount(dto);
        writeRefreshTokenCookie(request, response, loginVO.getRefreshToken(), Constants.REFRESH_TOKEN_EXPIRE_TIME);
        return Result.success(loginVO);
    }

    @PostMapping("/token/refresh")
    public Result<TokenRefreshVO> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractRefreshToken(request);
        TokenRefreshVO tokenRefreshVO = userService.refreshToken(refreshToken);
        writeRefreshTokenCookie(request, response, tokenRefreshVO.getRefreshToken(), Constants.REFRESH_TOKEN_EXPIRE_TIME);
        return Result.success(tokenRefreshVO);
    }

    @LoginCheck
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        userService.logout(UserThreadLocal.getUserId(), UserThreadLocal.getSessionId());
        clearRefreshTokenCookie(request, response);
        UserThreadLocal.removeUser();
        return Result.success(null);
    }

    @LoginCheck
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        return Result.success(userService.getCurrentUser(UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        userService.changePassword(UserThreadLocal.getUserId(), dto);
        clearRefreshTokenCookie(request, response);
        UserThreadLocal.removeUser();
        return Result.success(null);
    }

    @LoginCheck
    @PutMapping("/info")
    public Result<Void> updateUserInfo(@Valid @RequestBody UpdateUserInfoDTO dto) {
        userService.updateUserInfo(UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }

    @LoginCheck
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("avatar") MultipartFile avatar) {
        return Result.success(userService.uploadAvatar(UserThreadLocal.getUserId(), avatar));
    }

    @LoginCheck
    @PostMapping("/cancel")
    public Result<Void> cancelAccount() {
        userService.cancelAccount(UserThreadLocal.getUserId());
        return Result.success(null);
    }

    @LoginCheck
    @PutMapping("/status")
    public Result<Void> switchUserStatus(@Valid @RequestBody UserStatusDTO dto) {
        userService.switchUserStatus(UserThreadLocal.getUserId(), dto.getStatus());
        return Result.success(null);
    }

    @LoginCheck
    @GetMapping("/{accountId}")
    public Result<UserVO> getUserById(@PathVariable("accountId") Long accountId) {
        return Result.success(userService.getUserVOByAccountId(accountId));
    }
    @LoginCheck
    @GetMapping("/simple/{accountId}")
    public Result<UserSimpleVO> getUserSimpleById(@PathVariable("accountId") Long accountId) {
        return Result.success(userService.getUserSimpleByAccountId(accountId));
    }
    @LoginCheck
    @GetMapping("/ids")
    public Result<List<UserVO>> getUsersByIds(@RequestParam("ids") List<Long> ids) {
        return Result.success(userService.getUsersByIds(ids));
    }

    @LoginCheck
    @GetMapping("/simple/search")
    public PageResult<UserSimpleVO> getUserSimplePageByUsernamePrefix(@Valid UserSearchPageDTO dto) {
        return userService.getUserSimplePageByUsernamePrefix(dto.getPage(), dto.getSize(), dto.getUsername());
    }

    private String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (Constants.REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void writeRefreshTokenCookie(HttpServletRequest request, HttpServletResponse response,
                                         String refreshToken, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(Constants.REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path(Constants.REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(Constants.REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Lax")
                .path(Constants.REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
