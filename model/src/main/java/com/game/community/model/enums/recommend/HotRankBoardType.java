package com.game.community.model.enums.recommend;

import java.util.Locale;

/**
 * 热榜类型：总榜 / 周榜 / 日榜
 */
public enum HotRankBoardType {

    TOTAL("total"),
    WEEKLY("weekly"),
    DAILY("daily");

    private final String code;

    HotRankBoardType(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static HotRankBoardType fromCode(String board) {
        if (board == null || board.isBlank()) {
            return TOTAL;
        }
        String normalized = board.trim().toLowerCase(Locale.ROOT);
        for (HotRankBoardType value : values()) {
            if (value.code.equals(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException("不支持的榜单类型: " + board);
    }
}
