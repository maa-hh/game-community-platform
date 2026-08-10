package com.game.community.model.dto.game;

import lombok.Data;

import java.io.Serializable;

/**
 * 游戏短评分页查询参数。
 */
@Data
public class GameReviewPageQuery implements Serializable {

    /** 游戏 Steam App ID，由路径参数写入。 */
    private Long appId;

    /** 页码，从 1 开始。 */
    private Long page = 1L;

    /** 每页条数，服务端会限制最大值。 */
    private Long size = 10L;

    /** 排序方式：latest 最近发布，hot 最高点赞。 */
    private String sort = "latest";
}
