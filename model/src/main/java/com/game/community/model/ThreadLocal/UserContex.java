package com.game.community.model.ThreadLocal;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 当前请求用户上下文
 */
@Data
@NoArgsConstructor
public class UserContex implements Serializable {

    private Long userId;

    private Integer type;

    private String steamAccount;

    private String sessionId;

    public UserContex(Long userId, Integer type, String steamAccount) {
        this.userId = userId;
        this.type = type;
        this.steamAccount = steamAccount;
    }

    public UserContex(Long userId, Integer type, String steamAccount, String sessionId) {
        this.userId = userId;
        this.type = type;
        this.steamAccount = steamAccount;
        this.sessionId = sessionId;
    }
}
