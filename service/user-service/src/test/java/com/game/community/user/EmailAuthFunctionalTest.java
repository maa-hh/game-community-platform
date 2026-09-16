package com.game.community.user;

import com.game.community.common.constant.AuthErrorCodes;
import com.game.community.common.constant.user.RedisConstants;
import com.game.community.common.constant.user.UserConstants;
import com.game.community.model.base.Result;
import com.game.community.model.dto.user.AccountLoginDTO;
import com.game.community.model.dto.user.RegisterDTO;
import com.game.community.model.dto.user.ResetPasswordDTO;
import com.game.community.model.dto.user.SendCodeDTO;
import com.game.community.model.enums.user.CodeBizType;
import com.game.community.model.vo.user.LoginVO;
import com.game.community.model.vo.user.RegisterVO;
import com.game.community.model.vo.user.SendCodeVO;
import com.game.community.model.vo.user.TokenRefreshVO;
import com.game.community.user.service.UserAuthService;
import com.game.community.user.testsupport.AbstractUserServiceIntegrationTest;
import com.game.community.utils.RedisUtils;
import com.game.community.utils.web.ClientInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAuthFunctionalTest extends AbstractUserServiceIntegrationTest {

    @Autowired
    private UserAuthService userAuthService;

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

    @Test
    void emailRegisterLoginAndRefreshShouldWork() {
        String email = "player@game.com";
        String password = "abc123";

        Result<SendCodeVO> sendResult = userAuthService.sendCode(sendCodeDto(email, CodeBizType.REGISTER));
        assertThat(sendResult.getData().getExpireIn()).isEqualTo(300);

        String code = redisUtils.get(RedisConstants.codeKey(CodeBizType.REGISTER.getCode(), email));
        assertThat(code).isNotBlank();

        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setEmail(email);
        registerDTO.setPassword(password);
        registerDTO.setCode(code);
        Result<RegisterVO> registerResult = userAuthService.register(registerDTO);
        assertThat(registerResult.getData().getEmail()).isEqualTo(email.toLowerCase());

        AccountLoginDTO loginDTO = new AccountLoginDTO();
        loginDTO.setEmail(email);
        loginDTO.setPassword(password);
        Result<LoginVO> loginResult = userAuthService.login(loginDTO, clientInfo());
        LoginVO loginVO = loginResult.getData();

        assertThat(loginVO.getAccessToken()).isNotBlank();
        assertThat(loginVO.getAccessExpiresIn()).isPositive();
        assertThat(loginVO.getUser().getEmail()).isEqualTo(email.toLowerCase());
        assertThat(loginVO.getUser().getUsername()).isEqualTo("player");
        assertThat(loginVO.getRefreshToken()).isNotBlank();

        Result<TokenRefreshVO> refreshResult = userAuthService.refreshToken(loginVO.getRefreshToken());
        TokenRefreshVO refreshVO = refreshResult.getData();
        assertThat(refreshVO.getAccessToken()).isNotBlank();
        assertThat(refreshVO.getAccessExpiresIn()).isPositive();
        assertThat(refreshVO.getRefreshToken()).isNotBlank();
    }

    @Test
    void registerShouldBoundGeneratedUsernameToColumnLimit() {
        String email = "abcdefghijklmnopqrstuvwx@game.com";
        String password = "abc123";

        userAuthService.sendCode(sendCodeDto(email, CodeBizType.REGISTER));
        String code = redisUtils.get(RedisConstants.codeKey(CodeBizType.REGISTER.getCode(), email));
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setEmail(email);
        registerDTO.setPassword(password);
        registerDTO.setCode(code);

        userAuthService.register(registerDTO);

        AccountLoginDTO loginDTO = new AccountLoginDTO();
        loginDTO.setEmail(email);
        loginDTO.setPassword(password);
        LoginVO loginVO = userAuthService.login(loginDTO, clientInfo()).getData();
        assertThat(loginVO.getUser().getUsername())
                .isEqualTo(email.substring(0, UserConstants.USERNAME_MAX_LENGTH));
    }

    @Test
    void loginShouldRejectUnknownEmail() {
        AccountLoginDTO loginDTO = new AccountLoginDTO();
        loginDTO.setEmail("missing@game.com");
        loginDTO.setPassword("abc123");

        assertThatThrownBy(() -> userAuthService.login(loginDTO, clientInfo()))
                .hasMessage("该邮箱未注册");
    }

    @Test
    void refreshShouldUseBusinessCode40102() {
        assertThatThrownBy(() -> userAuthService.refreshToken(null))
                .isInstanceOf(com.game.community.common.exception.BusinessException.class)
                .satisfies(ex -> assertThat(((com.game.community.common.exception.BusinessException) ex).getCode())
                        .isEqualTo(AuthErrorCodes.REFRESH_EXPIRED));
    }

    @Test
    void verifyCodeShouldLockAfterTooManyFailures() {
        String email = "lock@test.com";
        userAuthService.sendCode(sendCodeDto(email, CodeBizType.REGISTER));
        String correctCode = redisUtils.get(RedisConstants.codeKey(CodeBizType.REGISTER.getCode(), email));

        RegisterDTO dto = new RegisterDTO();
        dto.setEmail(email);
        dto.setPassword("abc123");

        for (int i = 1; i < UserConstants.CODE_VERIFY_FAIL_THRESHOLD; i++) {
            dto.setCode("000000");
            assertThatThrownBy(() -> userAuthService.register(dto)).hasMessage("验证码错误");
            assertThat(redisUtils.get(RedisConstants.CODE_VERIFY_FAIL_PREFIX + email))
                    .isEqualTo(String.valueOf(i));
        }

        dto.setCode("000000");
        assertThatThrownBy(() -> userAuthService.register(dto)).hasMessage("验证码错误");

        dto.setCode(correctCode);
        assertThatThrownBy(() -> userAuthService.register(dto))
                .hasMessageContaining("验证码验证失败次数过多");
    }

    @Test
    void sendCodeShouldValidateRateLimit() {
        String email = "rate@test.com";
        userAuthService.sendCode(sendCodeDto(email, CodeBizType.REGISTER));
        assertThatThrownBy(() -> userAuthService.sendCode(sendCodeDto(email, CodeBizType.REGISTER)))
                .hasMessageContaining("发送过于频繁");
    }

    @Test
    void sendCodeShouldRejectWhenDailyLimitExceeded() {
        String email = "limit@test.com";
        redisUtils.setEx(RedisConstants.SEND_CODE_DAILY_PREFIX + "REGISTER:" + email,
                String.valueOf(UserConstants.SEND_CODE_DAILY_LIMIT), 3600);

        assertThatThrownBy(() -> userAuthService.sendCode(sendCodeDto(email, CodeBizType.REGISTER)))
                .hasMessage("今日发送次数已达上限，请明天再试");
    }

    @Test
    void sendCodeShouldRejectRegisteredEmail() {
        String email = "player@game.com";
        String password = "abc123";
        userAuthService.sendCode(sendCodeDto(email, CodeBizType.REGISTER));
        String code = redisUtils.get(RedisConstants.codeKey(CodeBizType.REGISTER.getCode(), email));

        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setEmail(email);
        registerDTO.setPassword(password);
        registerDTO.setCode(code);
        userAuthService.register(registerDTO);

        assertThatThrownBy(() -> userAuthService.sendCode(sendCodeDto(email, CodeBizType.REGISTER)))
                .hasMessage("该邮箱已被注册");
    }

    @Test
    void resetPasswordShouldWork() {
        String email = "reset@game.com";
        String oldPassword = "abc123";
        String newPassword = "pass99";

        userAuthService.sendCode(sendCodeDto(email, CodeBizType.REGISTER));
        String regCode = redisUtils.get(RedisConstants.codeKey(CodeBizType.REGISTER.getCode(), email));
        RegisterDTO registerDTO = new RegisterDTO();
        registerDTO.setEmail(email);
        registerDTO.setPassword(oldPassword);
        registerDTO.setCode(regCode);
        userAuthService.register(registerDTO);

        userAuthService.sendCode(sendCodeDto(email, CodeBizType.RESET_PASSWORD));
        String resetCode = redisUtils.get(
                RedisConstants.codeKey(CodeBizType.RESET_PASSWORD.getCode(), email));

        ResetPasswordDTO resetDTO = new ResetPasswordDTO();
        resetDTO.setEmail(email);
        resetDTO.setPassword(newPassword);
        resetDTO.setCode(resetCode);
        userAuthService.resetPassword(resetDTO);

        AccountLoginDTO oldLogin = new AccountLoginDTO();
        oldLogin.setEmail(email);
        oldLogin.setPassword(oldPassword);
        assertThatThrownBy(() -> userAuthService.login(oldLogin, clientInfo()))
                .hasMessage("密码错误");

        AccountLoginDTO newLogin = new AccountLoginDTO();
        newLogin.setEmail(email);
        newLogin.setPassword(newPassword);
        assertThat(userAuthService.login(newLogin, clientInfo()).getData().getAccessToken())
                .isNotBlank();
    }

    @Test
    void sendCodeShouldRejectCancelAccountBizType() {
        assertThatThrownBy(() -> userAuthService.sendCode(
                sendCodeDto("player@game.com", CodeBizType.CANCEL_ACCOUNT)))
                .hasMessage("请登录后在账号安全中申请注销验证码");
    }

    @Test
    void resetPasswordSendCodeShouldRejectUnknownEmail() {
        assertThatThrownBy(() -> userAuthService.sendCode(
                sendCodeDto("unknown@game.com", CodeBizType.RESET_PASSWORD)))
                .hasMessage("该邮箱未注册");
    }
}
