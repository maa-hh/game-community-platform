package com.game.community.steam.service;

import com.game.community.model.vo.game.GameChartItemVO;

import java.util.List;

public interface GameChartService {

    List<GameChartItemVO> listChart(String board);

    void syncChart(String board);

    void syncAllCharts();

    /** 按用户请求的页码按需补充榜单快照，避免启动时拉取完整榜单详情。 */
    void ensureChartPage(String board, long requiredCount);

    /** 当前榜单是否正在后台抓取下一批 Steam 榜单数据。 */
    boolean isExpansionInProgress(String board);

    boolean isAnyChartEmpty();
}
