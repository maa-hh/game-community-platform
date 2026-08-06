package com.game.community.steam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.game.UserSteamBind;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserSteamBindMapper extends BaseMapper<UserSteamBind> {

    @Select("SELECT * FROM t_user_steam_bind WHERE steam_id = #{steamId} LIMIT 1")
    UserSteamBind selectBySteamId(@Param("steamId") String steamId);
}
