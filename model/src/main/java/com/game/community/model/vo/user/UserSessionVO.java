package com.game.community.model.vo.user;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Redis 中缓存的用户会话
 */
@Data
public class UserSessionVO {

    private Long userId;

    private Integer type;

    private String gameAccount;

    private String refreshTokenHash;

    private String status;

    private LocalDateTime loginTime;

    private LocalDateTime lastRefreshTime;
}
