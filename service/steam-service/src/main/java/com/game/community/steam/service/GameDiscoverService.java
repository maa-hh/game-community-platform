package com.game.community.steam.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.game.GameDiscoverQuery;
import com.game.community.model.vo.game.GameChartItemVO;

public interface GameDiscoverService {

    /** 分页查询游戏发现数据，统一处理榜单、筛选和排序参数。 */
    PageResult<GameChartItemVO> pageDiscover(GameDiscoverQuery query);
}
