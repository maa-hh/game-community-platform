package com.game.community.model.dto.steam;

import lombok.Data;

import java.io.Serializable;

/**
 * Steam Store 轻量搜索参数。
 */
@Data
public class SteamAppSearchQuery implements Serializable {

    /** 搜索关键词，对外参数名为 q。 */
    private String q;

    /** 结果偏移量。 */
    private Integer start = 0;

    /** 返回条数。 */
    private Integer size = 20;

    /** 返回去除首尾空白后的搜索关键词。 */
    public String getKeyword() {
        return q == null ? "" : q.trim();
    }
}
