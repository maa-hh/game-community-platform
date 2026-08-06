package com.game.community.user.service;

import com.game.community.model.base.Result;
import com.game.community.model.dto.user.AccountLoginDTO;
import com.game.community.model.dto.user.RegisterDTO;
import com.game.community.model.dto.user.ResetPasswordDTO;
import com.game.community.model.dto.user.SendCodeDTO;
import com.game.community.model.vo.user.LoginVO;
import com.game.community.model.vo.user.RegisterVO;
import com.game.community.model.vo.user.SendCodeVO;
import com.game.community.model.vo.user.TokenRefreshVO;
import com.game.community.utils.web.ClientInfo;

/**
 * 用户认证（纯业务，不读写 Cookie / Servlet）。
 * <p>
 * 令牌约定：access 放响应 JSON；refresh 放 {@link LoginVO} / {@link TokenRefreshVO} 的
 * {@code refreshToken} 字段（@JsonIgnore），由 Controller 写 HttpOnly Cookie。
 */
public interface UserAuthService {

    /** 入参 email+bizType；出参 expireIn(秒)。仅 REGISTER / RESET_PASSWORD。 */
    Result<SendCodeVO> sendCode(SendCodeDTO dto);

    /** 入参 email+password+code；出参 email。不发 token。 */
    Result<RegisterVO> register(RegisterDTO dto);

    /** 入参 email+password+code；作废该用户全部 Redis 会话。 */
    Result<Void> resetPassword(ResetPasswordDTO dto);

    /** 入参 email+password、clientInfo.ip；出参 accessToken+user+refreshToken(写 Cookie 用)。 */
    Result<LoginVO> login(AccountLoginDTO dto, ClientInfo clientInfo);

    /** 入参 refresh JWT 字符串；出参新 accessToken+新 refreshToken(写 Cookie 用)。 */
    Result<TokenRefreshVO> refreshToken(String refreshToken);

    /**
     * 作废 Redis 会话。
     *
     * @param userId       Long，来自 access 上下文，可为 null
     * @param sessionId    String，会话 ID，可为 null
     * @param refreshToken String，Cookie 中的 refresh，userId 为空时用于解析
     */
    Result<Void> logout(Long userId, String sessionId, String refreshToken);
}
