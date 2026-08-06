package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.cosmetic.UserCosmetic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserCosmeticMapper extends BaseMapper<UserCosmetic> {

    @Select("SELECT * FROM t_user_cosmetic WHERE user_id = #{userId} AND cosmetic_code = #{code} LIMIT 1")
    UserCosmetic selectByUserAndCode(@Param("userId") Long userId, @Param("code") String code);

    @Update("UPDATE t_user_cosmetic SET quantity = quantity + #{delta}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND cosmetic_code = #{code} AND quantity + #{delta} >= 0")
    int increaseQuantity(@Param("userId") Long userId, @Param("code") String code, @Param("delta") int delta);
}
