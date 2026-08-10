package com.game.community.steam.service;

import com.game.community.model.dto.game.FollowGameDTO;
import com.game.community.model.vo.game.UserGameFollowVO;

import java.util.List;
import java.util.Map;

public interface SteamFollowService {

    /** 查询当前用户关注的游戏。 */
    List<UserGameFollowVO> listFollows();

    /** 查询当前用户是否关注指定游戏。 */
    boolean isFollowed(Long appId);

    /** 批量查询当前用户的游戏关注状态。 */
    Map<Long, Boolean> checkFollowBatch(List<Long> appIds);

    /** 保存当前用户对游戏的关注关系。 */
    void follow(FollowGameDTO dto);

    /** 删除当前用户对指定游戏的关注关系。 */
    void unfollow(Long appId);

    /** 将当前用户 Steam 游戏库导入为关注关系。 */
    int importFromSteam();
}
