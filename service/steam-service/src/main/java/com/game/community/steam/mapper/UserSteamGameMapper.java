package com.game.community.steam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.game.UserSteamGame;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserSteamGameMapper extends BaseMapper<UserSteamGame> {

    @Delete("DELETE FROM t_user_steam_game WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
