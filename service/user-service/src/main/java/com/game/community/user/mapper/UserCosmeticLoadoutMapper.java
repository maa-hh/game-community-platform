package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.cosmetic.UserCosmeticLoadout;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /** 按槽位清空当前装备；显式置 NULL，不能使用默认的 updateById。 */
    @Update({
            "<script>",
            "UPDATE t_user_cosmetic_loadout",
            "<set>",
            "<choose>",
            "<when test=\"slot == 'AVATAR_FRAME'\">avatar_frame_code = NULL,</when>",
            "<when test=\"slot == 'COMMENT_CARD'\">comment_card_code = NULL,</when>",
            "<when test=\"slot == 'COMMENT_FONT'\">comment_font_code = NULL,</when>",
            "<when test=\"slot == 'POST_CARD'\">post_card_code = NULL,</when>",
            "<when test=\"slot == 'PROFILE_BG'\">profile_bg_code = NULL,</when>",
            "<otherwise>user_id = user_id,</otherwise>",
            "</choose>",
            "version = version + 1,",
            "update_time = NOW()",
            "</set>",
            "WHERE user_id = #{userId}",
            "AND version = #{version}",
            "</script>"
    })
    int clearSlot(@Param("userId") Long userId,
                  @Param("slot") String slot,
                  @Param("version") Integer version);
}
