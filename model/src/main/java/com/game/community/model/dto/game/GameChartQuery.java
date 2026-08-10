package com.game.community.model.dto.game;

import lombok.Data;

import java.io.Serializable;

/**
 * 游戏榜单查询参数。
 */
@Data
public class GameChartQuery implements Serializable {

    /** 榜单类型：hot / new / free / discount。 */
    private String board = "hot";
}
