package com.game.community.steam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.game.UserSteamGame;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserSteamGameMapper extends BaseMapper<UserSteamGame> {

    @Delete("DELETE FROM t_user_steam_game WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /** 完整同步成功后标记本轮未出现的旧游戏，历史记录不物理删除。 */
    @Update("""
            UPDATE t_user_steam_game
            SET is_owned = 0
            WHERE user_id = #{userId}
              AND (sync_id IS NULL OR sync_id <> #{syncId})
            """)
    int markNotInSync(@Param("userId") Long userId, @Param("syncId") String syncId);

}
