package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.cosmetic.UserCosmeticLoadout;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserCosmeticLoadoutMapper extends BaseMapper<UserCosmeticLoadout> {

    /** 幂等初始化用户装扮槽位。 */
    @Insert("INSERT IGNORE INTO t_user_cosmetic_loadout "
            + "(user_id, avatar_frame_code, comment_card_code, comment_font_code, post_card_code, profile_bg_code, version, update_time) "
            + "VALUES (#{userId}, #{avatarFrameCode}, #{commentCardCode}, #{commentFontCode}, "
            + "#{postCardCode}, #{profileBgCode}, #{version}, #{updateTime})")
    int insertIgnore(UserCosmeticLoadout loadout);

    /** 批量查询多个用户的装扮槽位。 */
    @Select({
            "<script>",
            "SELECT * FROM t_user_cosmetic_loadout WHERE user_id IN",
            "<foreach collection='userIds' item='userId' open='(' separator=',' close=')'>",
            "#{userId}",
            "</foreach>",
            "</script>"
    })
    List<UserCosmeticLoadout> selectByUserIds(@Param("userIds") List<Long> userIds);
}
