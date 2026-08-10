package com.game.community.model.dto.game;

import lombok.Data;

import java.io.Serializable;

/**
 * 游戏目录分页查询参数。
 */
@Data
public class GamePageQuery implements Serializable {

    /** 排序方式：hot / score / discuss。 */
    private String sort = "hot";

    /** 页码，从 1 开始。 */
    private Long page = 1L;

    /** 每页条数，服务端会限制最大值。 */
    private Long size = 20L;
}
