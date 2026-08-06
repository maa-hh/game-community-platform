package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.cosmetic.UserCosmeticLoadout;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserCosmeticLoadoutMapper extends BaseMapper<UserCosmeticLoadout> {

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
