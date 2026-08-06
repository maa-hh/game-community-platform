package com.game.community.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.Constants;
import com.game.community.common.constant.user.RedisConstants;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.ThreadLocal.UserContex;
import com.game.community.model.dto.user.AccountLoginDTO;
import com.game.community.model.dto.user.CancelAccountDTO;
import com.game.community.model.dto.user.ChangePasswordDTO;
import com.game.community.model.dto.user.RegisterDTO;
import com.game.community.model.dto.user.SendCodeDTO;
import com.game.community.model.entity.user.User;
import com.game.community.model.entity.user.UserAccount;
import com.game.community.model.entity.user.UserAuth;
import com.game.community.model.enums.user.AccountType;
import com.game.community.model.enums.user.CodeBizType;
import com.game.community.model.enums.user.UserAccountStatus;
import com.game.community.model.vo.user.LoginVO;
import com.game.community.model.vo.user.UserCardVO;
import com.game.community.model.vo.user.UserPublicVO;
import com.game.community.user.service.UserAccountService;
import com.game.community.user.service.UserAuthService;
import com.game.community.user.service.UserProfileService;
import com.game.community.user.service.UserQueryService;
import com.game.community.user.testsupport.AbstractUserServiceIntegrationTest;
import com.game.community.utils.EncryptUtils;
import com.game.community.utils.JwtUtils;
import com.game.community.utils.RedisUtils;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import com.game.community.utils.web.ClientInfo;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用户服务核心功能集成测试（真实 Redis + H2）
 */
class UserServiceFunctionalTest extends AbstractUserServiceIntegrationTest {

    @Autowired
    private UserAuthService userAuthService;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private UserQueryService userQueryService;

    @Autowired
    private UserAccountService userAccountService;

    @Autowired
    private RedisUtils redisUtils;

    private ClientInfo clientInfo() {
        return ClientInfo.from(request());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private SendCodeDTO sendCodeDto(String email, CodeBizType bizType) {
        SendCodeDTO dto = new SendCodeDTO();
        dto.setEmail(email);
        dto.setBizType(bizType);
        return dto;
    }

    private String registerUser(String email, String password) {
        userAuthService.sendCode(sendCodeDto(email, CodeBizType.REGISTER));
        String code = redisUtils.get(
                RedisConstants.codeKey(CodeBizType.REGISTER.getCode(), email.toLowerCase()));
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setEmail(email);
        registerDTO.setPassword(password);
        registerDTO.setCode(code);
        return userAuthService.register(registerDTO).getData().getEmail();
    }

    @Test
    void registerLoginUpdateAndChangePasswordShouldWork() {
        String email = "user1@game.com";
        String password = "abc123";

        registerUser(email, password);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        assertThat(user).isNotNull();
        Long userId = user.getId();
        Long accountId = user.getAccountId();
        assertThat(accountId).isGreaterThanOrEqualTo(UserConstants.ACCOUNT_ID_MIN);

        AccountLoginDTO loginDTO = new AccountLoginDTO();
        loginDTO.setEmail(email);
        loginDTO.setPassword(password);
        LoginVO loginVO = userAuthService.login(loginDTO, clientInfo()).getData();

        assertThat(loginVO.getAccessToken()).isNotBlank();
        assertThat(loginVO.getUser().getUserId()).isEqualTo(userId);

        Claims claims = JwtUtils.parseToken(Constants.ACCESS_JWT_SECRET, loginVO.getAccessToken());
        assertThat(((Number) claims.get("accountId")).longValue()).isEqualTo(accountId);
        assertThat(claims.get("userId")).isNull();

        UserPublicVO userVO = userQueryService.getUserPublicByAccountId(accountId).getData();
        assertThat(userVO.getUsername()).isEqualTo("user1");
        assertThat(userVO.getAccountId()).isEqualTo(accountId);

        List<UserCardVO> cards = userQueryService.getUsersByAccountIds(List.of(accountId)).getData();
        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).getAccountId()).isEqualTo(accountId);
        assertThat(cards.get(0).getUsername()).isEqualTo("user1");

        UserAuth createdAuth = userAuthMapper.selectOne(new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getUserId, userId));
        assertThat(createdAuth.getLastPasswordChange()).isNotNull();

        UserThreadLocal.setUser(new UserContex(userId, AccountType.NORMAL.getCode(), ""));
        try {
            ChangePasswordDTO wrongPasswordDto = new ChangePasswordDTO();
            wrongPasswordDto.setOldPassword("wrong-old");
            wrongPasswordDto.setNewPassword("pass5678");
            wrongPasswordDto.setConfirmPassword("pass5678");
            assertThatThrownBy(() -> userProfileService.changePassword(wrongPasswordDto))
                    .hasMessage("原密码错误");

            ChangePasswordDTO changePasswordDTO = new ChangePasswordDTO();
            changePasswordDTO.setOldPassword(password);
            changePasswordDTO.setNewPassword("pass5678");
            changePasswordDTO.setConfirmPassword("pass5678");
            userProfileService.changePassword(changePasswordDTO);
        } finally {
            UserThreadLocal.removeUser();
        }

        UserAuth updatedAuth = userAuthMapper.selectOne(new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getUserId, userId));
        assertThat(updatedAuth).isNotNull();
        assertThat(updatedAuth.getPassword()).startsWith("$2a$");

        loginDTO.setPassword(password);
        assertThatThrownBy(() -> userAuthService.login(loginDTO, clientInfo()))
                .hasMessage("密码错误");

        loginDTO.setPassword("pass5678");
        LoginVO reloginVO = userAuthService.login(loginDTO, clientInfo()).getData();
        assertThat(reloginVO.getAccessToken()).isNotBlank();
    }

    @Test
    void banAndUnbanUserShouldWork() {
        String email = "ban@test.com";
        String password = "abc123";
        registerUser(email, password);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        Long userId = user.getId();

        userAccountService.banUser(userId, "测试封禁", 24, 1L);

        UserAccount account = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId));
        assertThat(account.getStatus()).isEqualTo(UserAccountStatus.BANNED);

        AccountLoginDTO loginDTO = new AccountLoginDTO();
        loginDTO.setEmail(email);
        loginDTO.setPassword(password);
        assertThatThrownBy(() -> userAuthService.login(loginDTO, clientInfo()))
                .hasMessageContaining("账号被封禁");

        userAccountService.unbanUser(userId, 1L);

        LoginVO loginVO = userAuthService.login(loginDTO, clientInfo()).getData();
        assertThat(loginVO.getAccessToken()).isNotBlank();
    }

    @Test
    void cancelAndRevokeAccountShouldWork() {
        String email = "cancel@test.com";
        String password = "abc123";
        registerUser(email, password);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        Long userId = user.getId();

        UserThreadLocal.setUser(new UserContex(userId, AccountType.NORMAL.getCode(), ""));
        try {
            userAccountService.sendCancelAccountCode();
            String code = redisUtils.get(
                    RedisConstants.codeKey(CodeBizType.CANCEL_ACCOUNT.getCode(), email.toLowerCase()));

            CancelAccountDTO cancelDTO = new CancelAccountDTO();
            cancelDTO.setCode(code);
            userAccountService.cancelAccount(cancelDTO);
        } finally {
            UserThreadLocal.removeUser();
        }

        UserAccount account = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId));
        assertThat(account.getStatus()).isEqualTo(UserAccountStatus.CANCELLING);

        AccountLoginDTO loginDTO = new AccountLoginDTO();
        loginDTO.setEmail(email);
        loginDTO.setPassword(password);
        LoginVO loginVO = userAuthService.login(loginDTO, clientInfo()).getData();
        assertThat(loginVO.getAccessToken()).isNotBlank();

        account = userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId));
        assertThat(account.getStatus()).isEqualTo(UserAccountStatus.NORMAL);
    }
}
