package com.game.community.steam.service;

import com.game.community.model.dto.game.FollowGameDTO;
import com.game.community.model.vo.game.UserGameFollowVO;

import java.util.List;
import java.util.Map;

public interface SteamFollowService {

    List<UserGameFollowVO> listFollows(Long userId);

    boolean isFollowed(Long userId, Long appId);

    Map<Long, Boolean> checkFollowBatch(Long userId, List<Long> appIds);

    void follow(Long userId, FollowGameDTO dto);

    void unfollow(Long userId, Long appId);

    int importFromSteam(Long userId);
}
