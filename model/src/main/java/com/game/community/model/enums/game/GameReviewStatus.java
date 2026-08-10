package com.game.community.model.enums.game;

/**
 * 游戏短评记录状态。
 */
public enum GameReviewStatus {

    DELETED(0),
    ACTIVE(1);

    private final int code;

    GameReviewStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
