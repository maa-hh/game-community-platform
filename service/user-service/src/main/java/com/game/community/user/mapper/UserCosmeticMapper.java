package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.model.entity.cosmetic.UserCosmetic;
import com.game.community.common.constant.cosmetic.CosmeticConstants;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserCosmeticMapper extends BaseMapper<UserCosmetic> {

    IPage<UserCosmetic> selectBackpackPage(
            Page<UserCosmetic> page,
            @Param("userId") Long userId,
            @Param("effectMode") String effectMode,
            @Param("category") String category,
            @Param("equipped") Boolean equipped,
            @Param("state") String state,
            @Param("keyword") String keyword);

    @Select("SELECT * FROM t_user_cosmetic WHERE user_id = #{userId} AND cosmetic_code = #{code} LIMIT 1")
    UserCosmetic selectByUserAndCode(@Param("userId") Long userId, @Param("code") String code);

    @Insert("INSERT INTO t_user_cosmetic "
            + "(user_id, cosmetic_code, quantity, source_type, source_ref, acquired_at, create_time, update_time) "
            + "VALUES (#{userId}, #{code}, #{quantity}, #{sourceType}, #{sourceRef}, NOW(), NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE "
            + "quantity = CASE WHEN #{stackable} = " + CosmeticConstants.STACKABLE
            + " THEN quantity + #{quantity} ELSE quantity END, "
            + "update_time = NOW()")
    int upsertOwned(@Param("userId") Long userId, @Param("code") String code,
                    @Param("quantity") int quantity, @Param("sourceType") String sourceType,
                    @Param("sourceRef") String sourceRef, @Param("stackable") int stackable);

    @Update("UPDATE t_user_cosmetic SET quantity = quantity + #{delta}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND cosmetic_code = #{code} AND quantity + #{delta} >= 0")
    int increaseQuantity(@Param("userId") Long userId, @Param("code") String code, @Param("delta") int delta);
}
