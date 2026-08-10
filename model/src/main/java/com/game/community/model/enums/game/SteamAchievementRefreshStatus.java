package com.game.community.model.enums.game;

/**
 * Steam 用户成就快照刷新状态。
 */
public enum SteamAchievementRefreshStatus {

    LOADING("LOADING"),
    READY("READY"),
    FAILED("FAILED"),
    NOT_AVAILABLE("NOT_AVAILABLE");

    private final String code;

    SteamAchievementRefreshStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
