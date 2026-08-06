package com.game.community.steam.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.game.GameDiscoverQuery;
import com.game.community.model.vo.game.GameChartItemVO;

public interface GameDiscoverService {

    PageResult<GameChartItemVO> pageDiscover(GameDiscoverQuery query);
}
