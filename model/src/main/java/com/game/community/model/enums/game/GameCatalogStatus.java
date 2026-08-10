package com.game.community.model.enums.game;

/**
 * 游戏目录启用状态。
 */
public enum GameCatalogStatus {

    DISABLED(0),
    ENABLED(1);

    private final int code;

    GameCatalogStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
