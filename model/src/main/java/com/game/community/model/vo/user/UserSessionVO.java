package com.game.community.model.vo.user;

import com.game.community.model.enums.user.AccountType;
import com.game.community.model.enums.user.SessionStatus;
import com.game.community.model.enums.user.UserAccountStatus;
import com.game.community.model.enums.user.UserStrings;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Redis 中缓存的用户会话
 */
@Data
public class UserSessionVO {

    private Long userId;

    /** 对外账号 ID，与 access JWT 中的 accountId 交叉校验 */
    private Long accountId;

    private AccountType type;

    private String steamAccount;

    private UserAccountStatus accountStatus;

    private String refreshTokenHash;

    private SessionStatus status;

    private LocalDateTime loginTime;

    private LocalDateTime lastRefreshTime;

    public String getSteamAccount() {
        return UserStrings.orEmpty(steamAccount);
    }
}
