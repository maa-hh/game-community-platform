package com.game.community.model.vo.game;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GameChartItemVO extends GameListItemVO {

    private Integer rank;
}
