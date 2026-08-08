package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.cosmetic.UserActiveEffect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserActiveEffectMapper extends BaseMapper<UserActiveEffect> {

    /** 批量查询尚未过期的用户主动效果。 */
    @Select({
            "<script>",
            "SELECT * FROM t_user_active_effect",
            "WHERE user_id IN",
            "<foreach collection='userIds' item='userId' open='(' separator=',' close=')'>",
            "#{userId}",
            "</foreach>",
            "AND (expire_at IS NULL OR expire_at &gt; #{now})",
            "</script>"
    })
    List<UserActiveEffect> selectActiveByUserIds(@Param("userIds") List<Long> userIds,
                                                  @Param("now") LocalDateTime now);
}
