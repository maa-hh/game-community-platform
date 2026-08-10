package com.game.community.model.enums.game;

/**
 * 游戏目录从基础信息到完整详情的刷新状态。
 */
public enum GameCatalogRefreshStatus {

    BASIC_READY("BASIC_READY"),
    READY("READY");

    private final String code;

    GameCatalogRefreshStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
