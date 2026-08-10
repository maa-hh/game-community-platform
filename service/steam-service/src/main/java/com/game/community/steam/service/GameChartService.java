package com.game.community.steam.service;

import com.game.community.model.dto.game.GameChartQuery;
import com.game.community.model.vo.game.GameChartItemVO;

import java.util.List;

public interface GameChartService {

    /** 查询指定榜单的当前快照。 */
    List<GameChartItemVO> listChart(GameChartQuery query);

    /** 同步指定榜单的 Steam 快照。 */
    void syncChart(String board);

    /** 同步所有已配置榜单。 */
    void syncAllCharts();
}
