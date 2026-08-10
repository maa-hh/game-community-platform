package com.game.community.model.dto.game;

import lombok.Data;

import java.io.Serializable;

/**
 * 游戏目录搜索参数。
 *
 * <p>字段名使用 q，以保持现有 HTTP 查询参数契约。</p>
 */
@Data
public class GameSearchQuery implements Serializable {

    /** 搜索关键词，对外参数名为 q。 */
    private String q;

    /** 页码，从 1 开始。 */
    private Long page = 1L;

    /** 每页条数，服务端会限制最大值。 */
    private Long size = 20L;

    /** 返回去除首尾空白后的搜索关键词。 */
    public String getKeyword() {
        return q == null ? "" : q.trim();
    }
}
