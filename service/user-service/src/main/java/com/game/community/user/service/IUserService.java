package com.game.community.user.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.user.AccountLoginDTO;
import com.game.community.model.dto.user.ChangePasswordDTO;
import com.game.community.model.dto.user.RegisterDTO;
import com.game.community.model.dto.user.UpdateUserInfoDTO;
import com.game.community.model.vo.user.LoginVO;
import com.game.community.model.vo.user.TokenRefreshVO;
import com.game.community.model.vo.user.UserSimpleVO;
import com.game.community.model.vo.user.UserVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户服务接口
 */
public interface IUserService {

    String sendCode(String phone);

    Long registerByPhone(RegisterDTO dto);

    LoginVO loginByAccount(AccountLoginDTO dto);

    TokenRefreshVO refreshToken(String refreshToken);

    void logout(Long userId, String sessionId);

    UserVO getCurrentUser(Long userId);

    void changePassword(Long userId, ChangePasswordDTO dto);

    UserVO getUserVOById(Long id);

    UserVO getUserVOByAccountId(Long accountId);

    UserSimpleVO getUserSimpleById(Long id);

    UserSimpleVO getUserSimpleByAccountId(Long accountId);

    List<UserVO> getUsersByIds(List<Long> ids);

    PageResult<UserSimpleVO> getUserSimplePageByUsernamePrefix(Integer page, Integer size, String usernamePrefix);

    void updateUserInfo(Long userId, UpdateUserInfoDTO dto);

    String uploadAvatar(Long userId, MultipartFile avatarFile);

    void cancelAccount(Long userId);

    void switchUserStatus(Long userId, Integer status);
}
