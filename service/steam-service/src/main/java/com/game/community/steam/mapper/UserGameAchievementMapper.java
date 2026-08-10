package com.game.community.steam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.game.UserGameAchievement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserGameAchievementMapper extends BaseMapper<UserGameAchievement> {

    @Update("UPDATE t_user_game_achievement SET is_current = 0, update_time = NOW() "
            + "WHERE user_id = #{userId} AND app_id = #{appId} AND is_current = 1")
    int clearCurrent(@Param("userId") Long userId, @Param("appId") Long appId);
}
