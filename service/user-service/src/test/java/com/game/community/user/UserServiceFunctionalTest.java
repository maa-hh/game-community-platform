package com.game.community.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.Constants;
import com.game.community.common.constant.user.RedisConstants;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.dto.user.AccountLoginDTO;
import com.game.community.model.dto.user.ChangePasswordDTO;
import com.game.community.model.dto.user.RegisterDTO;
import com.game.community.model.dto.user.UpdateUserInfoDTO;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAuditTask;
import com.game.community.model.vo.user.LoginVO;
import com.game.community.model.vo.user.UserVO;
import com.game.community.user.mapper.UserAuditTaskMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.service.AvatarAuditTaskService;
import com.game.community.user.service.IUserService;
import com.game.community.user.service.UserAuditService;
import com.game.community.user.service.UserProfileAuditTaskService;
import com.game.community.utils.JwtUtils;
import com.game.community.utils.RedisUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户服务核心功能测试
 */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceFunctionalTest {

    @Autowired
    private IUserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserAuditTaskMapper userAuditTaskMapper;

    @MockBean
    private RedisUtils redisUtils;

    @MockBean
    private AvatarAuditTaskService avatarAuditTaskService;

    @MockBean
    private UserProfileAuditTaskService userProfileAuditTaskService;

    @MockBean
    private UserAuditService userAuditService;

    @Test
    void registerLoginUpdateAndCancelShouldWork() {
        when(redisUtils.setIfAbsent(eq(RedisConstants.REGISTER_LOCK_PREFIX + "13800138000"), anyString(), anyLong()))
                .thenReturn(true);
        when(redisUtils.setIfAbsent(eq(RedisConstants.LOGIN_LOCK_PREFIX + 1L), anyString(), anyLong()))
                .thenReturn(true);
        when(redisUtils.get(RedisConstants.CODE_PREFIX + "13800138000")).thenReturn("123456");
        when(userAuditService.auditUserInfo(anyString(), org.mockito.ArgumentMatchers.isNull())).thenReturn(true);

        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setUsername("玩家小明");
        registerDTO.setPassword("123456");
        registerDTO.setPhone("13800138000");
        registerDTO.setCode("123456");
        registerDTO.setGameAccount("game_10001");

        Long accountId = userService.registerByPhone(registerDTO);
        assertThat(accountId).isGreaterThanOrEqualTo(UserConstants.ACCOUNT_ID_MIN);
        verify(redisUtils).del(RedisConstants.CODE_PREFIX + "13800138000");

        AccountLoginDTO loginDTO = new AccountLoginDTO();
        loginDTO.setAccountId(accountId);
        loginDTO.setPassword("123456");
        loginDTO.setType(UserConstants.UserType.NORMAL);
        LoginVO loginVO = userService.loginByAccount(loginDTO);

        assertThat(loginVO.getAccessToken()).isNotBlank();
        assertThat(loginVO.getAccountId()).isEqualTo(accountId);
        Long userId = loginVO.getUserId();
        assertThat(userId).isNotNull();
        Claims claims = JwtUtils.parseToken(Constants.ACCESS_JWT_SECRET, loginVO.getAccessToken());
        assertThat(((Number) claims.get("userId")).longValue()).isEqualTo(userId);
        String sessionId = claims.get("sessionId", String.class);
        assertThat(sessionId).isNotBlank();
        verify(redisUtils).set(eq(RedisConstants.SESSION_PREFIX + sessionId), anyString(), anyLong(), eq(java.util.concurrent.TimeUnit.SECONDS));
        verify(redisUtils).set(eq(RedisConstants.ACTIVE_SESSION_PREFIX + userId), eq(sessionId), anyLong(), eq(java.util.concurrent.TimeUnit.SECONDS));

        UpdateUserInfoDTO updateDTO = new UpdateUserInfoDTO();
        updateDTO.setVersion(0);
        updateDTO.setUsername("新的昵称");
        updateDTO.setSignature("今天也想赢一局");
        updateDTO.setGameAccount("game_20002");
        userService.updateUserInfo(userId, updateDTO);

        UserVO userVO = userService.getUserVOById(userId);
        assertThat(userVO.getUsername()).isEqualTo("玩家小明");
        assertThat(userVO.getSignature()).isNull();
        assertThat(userVO.getGameAccount()).isEqualTo("game_10001");
        assertThat(userVO.getAuditStatus()).isEqualTo(UserConstants.AuditStatus.AUDITING);
        UserAuditTask profileTask = userAuditTaskMapper.selectOne(new LambdaQueryWrapper<UserAuditTask>()
                .eq(UserAuditTask::getUserId, userId)
                .eq(UserAuditTask::getTaskType, UserConstants.AuditTaskType.PROFILE));
        assertThat(profileTask).isNotNull();
        assertThat(profileTask.getStatus()).isEqualTo(UserConstants.AuditTaskStatus.PENDING);

        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getAuditStatus, UserConstants.AuditStatus.AUDITING));
        UpdateUserInfoDTO blockedUpdateDTO = new UpdateUserInfoDTO();
        blockedUpdateDTO.setVersion(1);
        blockedUpdateDTO.setSignature("审核中不允许再次修改");
        assertThatThrownBy(() -> userService.updateUserInfo(userId, blockedUpdateDTO))
                .hasMessage("用户资料审核中，请稍后再试");
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getAuditStatus, UserConstants.AuditStatus.NONE));

        UpdateUserInfoDTO staleVersionDto = new UpdateUserInfoDTO();
        staleVersionDto.setVersion(0);
        staleVersionDto.setSignature("旧版本提交");
        assertThatThrownBy(() -> userService.updateUserInfo(userId, staleVersionDto))
                .hasMessage("资料已更新，请刷新页面后重试");

        ChangePasswordDTO wrongPasswordDto = new ChangePasswordDTO();
        wrongPasswordDto.setOldPassword("wrong-old-password");
        wrongPasswordDto.setNewPassword("654321");
        assertThatThrownBy(() -> userService.changePassword(userId, wrongPasswordDto))
                .hasMessage("原密码错误");

        when(redisUtils.get(RedisConstants.ACTIVE_SESSION_PREFIX + userId)).thenReturn(sessionId);
        ChangePasswordDTO changePasswordDTO = new ChangePasswordDTO();
        changePasswordDTO.setOldPassword("123456");
        changePasswordDTO.setNewPassword("654321");
        userService.changePassword(userId, changePasswordDTO);
        verify(redisUtils, atLeastOnce()).del(RedisConstants.SESSION_PREFIX + sessionId);
        verify(redisUtils, atLeastOnce()).del(RedisConstants.ACTIVE_SESSION_PREFIX + userId);

        AccountLoginDTO reloginWithOldPassword = new AccountLoginDTO();
        reloginWithOldPassword.setAccountId(accountId);
        reloginWithOldPassword.setPassword("123456");
        reloginWithOldPassword.setType(UserConstants.UserType.NORMAL);
        assertThatThrownBy(() -> userService.loginByAccount(reloginWithOldPassword))
                .hasMessage("账号或密码错误");

        AccountLoginDTO reloginWithNewPassword = new AccountLoginDTO();
        reloginWithNewPassword.setAccountId(accountId);
        reloginWithNewPassword.setPassword("654321");
        reloginWithNewPassword.setType(UserConstants.UserType.NORMAL);
        LoginVO reloginVO = userService.loginByAccount(reloginWithNewPassword);
        assertThat(reloginVO.getAccessToken()).isNotBlank();
        String newSessionId = JwtUtils.parseToken(Constants.ACCESS_JWT_SECRET, reloginVO.getAccessToken())
                .get("sessionId", String.class);
        when(redisUtils.get(RedisConstants.ACTIVE_SESSION_PREFIX + userId)).thenReturn(newSessionId);
        userService.cancelAccount(userId);
        verify(redisUtils, atLeastOnce()).del(RedisConstants.SESSION_PREFIX + sessionId);
        verify(redisUtils, atLeastOnce()).del(RedisConstants.SESSION_PREFIX + newSessionId);
        verify(redisUtils, atLeastOnce()).del(RedisConstants.ACTIVE_SESSION_PREFIX + userId);

        assertThatThrownBy(() -> userService.getUserVOById(userId))
                .hasMessage("用户不存在");
    }

    @Test
    void sendCodeShouldWriteRedis() {
        String code = userService.sendCode("13800138001");

        assertThat(code).hasSize(6);
        verify(redisUtils).setEx(RedisConstants.CODE_PREFIX + "13800138001", code, UserConstants.CODE_EXPIRE);
    }
}
