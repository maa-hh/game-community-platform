package com.game.community.user.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.Result;
import com.game.community.model.dto.user.ChangePasswordDTO;
import com.game.community.model.dto.user.ConfirmChangeEmailDTO;
import com.game.community.model.dto.user.PrepareChangeEmailDTO;
import com.game.community.model.dto.user.UpdateSignatureDTO;
import com.game.community.model.dto.user.UpdateUserInfoDTO;
import com.game.community.model.dto.user.UpdateUsernameDTO;
import com.game.community.model.vo.user.ChangeEmailVO;
import com.game.community.model.vo.user.ProfileFieldSubmitVO;
import com.game.community.model.vo.user.SendCodeVO;
import com.game.community.model.vo.user.UserMeVO;
import com.game.community.user.service.UserProfileService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import com.game.community.utils.web.CookieHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户资料控制器：分字段修改、头像上传、修改密码/邮箱
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final CookieHelper cookieHelper;

    @LoginCheck
    /** 执行 getCurrentUser 对应的业务处理。 */
    @GetMapping("/me")
    public Result<UserMeVO> getCurrentUser() {
        return userProfileService.getCurrentUser();
    }

    @LoginCheck
    /** 执行 updateUsername 对应的业务处理。 */
    @PutMapping("/username")
    public Result<ProfileFieldSubmitVO> updateUsername(@Valid @RequestBody UpdateUsernameDTO dto) {
        return userProfileService.updateUsername(dto);
    }

    @LoginCheck
    /** 执行 updateSignature 对应的业务处理。 */
    @PutMapping("/signature")
    public Result<ProfileFieldSubmitVO> updateSignature(@Valid @RequestBody UpdateSignatureDTO dto) {
        return userProfileService.updateSignature(dto);
    }

    @LoginCheck
    /** 执行 uploadAvatar 对应的业务处理。 */
    @PostMapping("/avatar")
    public Result<ProfileFieldSubmitVO> uploadAvatar(@RequestParam("avatar") MultipartFile avatar,
                                                     @RequestParam("version") Integer version) {
        return userProfileService.uploadAvatar(avatar, version);
    }

    /** 更新无需审核的资料（Steam 账号） */
    @LoginCheck
    /** 执行 updateUserInfo 对应的业务处理。 */
    @PutMapping("/info")
    public Result<Void> updateUserInfo(@Valid @RequestBody UpdateUserInfoDTO dto) {
        return userProfileService.updateUserInfo(dto);
    }

    @LoginCheck
    /** 执行 changePassword 对应的业务处理。 */
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        Result<Void> result = userProfileService.changePassword(dto);
        cookieHelper.clearRefreshTokenCookie(request, response);
        UserThreadLocal.removeUser();
        return result;
    }

    /** 改邮箱第一步：向当前绑定邮箱发验证码 */
    @LoginCheck
    /** 执行 sendChangeEmailOldCode 对应的业务处理。 */
    @PostMapping("/email/send-old-code")
    public Result<SendCodeVO> sendChangeEmailOldCode() {
        return userProfileService.sendChangeEmailOldCode();
    }

    /** 改邮箱第二步：校验原邮箱验证码后，向新邮箱发码 */
    @LoginCheck
    /** 执行 prepareChangeEmail 对应的业务处理。 */
    @PostMapping("/email/prepare")
    public Result<SendCodeVO> prepareChangeEmail(@Valid @RequestBody PrepareChangeEmailDTO dto) {
        return userProfileService.prepareChangeEmail(dto);
    }

    /** 改邮箱第三步：双码确认更换（成功后清除登录态） */
    @LoginCheck
    /** 执行 confirmChangeEmail 对应的业务处理。 */
    @PutMapping("/email")
    public Result<ChangeEmailVO> confirmChangeEmail(@Valid @RequestBody ConfirmChangeEmailDTO dto,
                                                    HttpServletRequest request,
                                                    HttpServletResponse response) {
        Result<ChangeEmailVO> result = userProfileService.confirmChangeEmail(dto);
        cookieHelper.clearRefreshTokenCookie(request, response);
        UserThreadLocal.removeUser();
        return result;
    }
}
