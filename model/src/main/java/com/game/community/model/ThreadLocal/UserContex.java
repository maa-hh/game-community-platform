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

    private String gameAccount;

    private String sessionId;

    public UserContex(Long userId, Integer type, String gameAccount) {
        this.userId = userId;
        this.type = type;
        this.gameAccount = gameAccount;
    }

    public UserContex(Long userId, Integer type, String gameAccount, String sessionId) {
        this.userId = userId;
        this.type = type;
        this.gameAccount = gameAccount;
        this.sessionId = sessionId;
    }
}
