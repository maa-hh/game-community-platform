package com.game.community.user.service;

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
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户资料服务：查询、分字段修改、头像上传、修改密码/邮箱
 */
public interface UserProfileService {

    Result<UserMeVO> getCurrentUser();

    Result<ProfileFieldSubmitVO> updateUsername(UpdateUsernameDTO dto);

    Result<ProfileFieldSubmitVO> updateSignature(UpdateSignatureDTO dto);

    Result<ProfileFieldSubmitVO> uploadAvatar(MultipartFile avatarFile, Integer version);

    /** 更新无需审核的资料（Steam 账号等） */
    Result<Void> updateUserInfo(UpdateUserInfoDTO dto);

    /** 修改密码并使全部会话失效（清 Cookie 由 Controller 负责） */
    Result<Void> changePassword(ChangePasswordDTO dto);

    /** 向当前绑定邮箱发送改邮验证码 */
    Result<SendCodeVO> sendChangeEmailOldCode();

    /** 校验原邮箱验证码后，向新邮箱发码（原码不消费） */
    Result<SendCodeVO> prepareChangeEmail(PrepareChangeEmailDTO dto);

    /** 双码确认更换邮箱并使全部会话失效（清 Cookie 由 Controller 负责） */
    Result<ChangeEmailVO> confirmChangeEmail(ConfirmChangeEmailDTO dto);
}
