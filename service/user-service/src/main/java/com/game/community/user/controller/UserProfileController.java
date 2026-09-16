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

    /** 查询当前登录用户的资料、账户状态和各字段审核状态。 */
    @LoginCheck
    @GetMapping("/me")
    public Result<UserMeVO> getCurrentUser() {
        return userProfileService.getCurrentUser();
    }

    /** 接收昵称修改请求并提交字段审核任务。 */
    @LoginCheck
    @PutMapping("/username")
    public Result<ProfileFieldSubmitVO> updateUsername(@Valid @RequestBody UpdateUsernameDTO dto) {
        return userProfileService.updateUsername(dto);
    }

    /** 接收个性签名修改请求并提交字段审核任务。 */
    @LoginCheck
    @PutMapping("/signature")
    public Result<ProfileFieldSubmitVO> updateSignature(@Valid @RequestBody UpdateSignatureDTO dto) {
        return userProfileService.updateSignature(dto);
    }

    /** 接收头像文件，上传到私有对象存储并提交头像审核任务。 */
    @LoginCheck
    @PostMapping("/avatar")
    public Result<ProfileFieldSubmitVO> uploadAvatar(@RequestParam("avatar") MultipartFile avatar,
                                                     @RequestParam("version") Integer version) {
        return userProfileService.uploadAvatar(avatar, version);
    }

    /** 更新无需内容审核的用户资料字段。 */
    @LoginCheck
    @PutMapping("/info")
    public Result<Void> updateUserInfo(@Valid @RequestBody UpdateUserInfoDTO dto) {
        return userProfileService.updateUserInfo(dto);
    }

    /** 修改密码；成功后清理当前登录态。 */
    @LoginCheck
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto,
                                       HttpServletRequest request,
                                       HttpServletResponse response) {
        Result<Void> result = userProfileService.changePassword(dto);
        cookieHelper.clearRefreshTokenCookie(request, response);
        UserThreadLocal.removeUser();
        return result;
    }

    /** 向当前绑定邮箱发送改邮箱第一步验证码。 */
    @LoginCheck
    @PostMapping("/email/send-old-code")
    public Result<SendCodeVO> sendChangeEmailOldCode() {
        return userProfileService.sendChangeEmailOldCode();
    }

    /** 校验原邮箱验证码，并向新邮箱发送确认验证码。 */
    @LoginCheck
    @PostMapping("/email/prepare")
    public Result<SendCodeVO> prepareChangeEmail(@Valid @RequestBody PrepareChangeEmailDTO dto) {
        return userProfileService.prepareChangeEmail(dto);
    }

    /** 校验双邮箱验证码、更新邮箱并清理全部登录态。 */
    @LoginCheck
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
