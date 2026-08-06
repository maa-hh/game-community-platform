package com.game.community.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import com.game.community.model.entity.shop.ShopUserCurrency;

@Mapper
public interface ShopUserCurrencyMapper extends BaseMapper<ShopUserCurrency> {

    @Insert("INSERT IGNORE INTO t_shop_user_currency(user_id, points, create_time, update_time) "
            + "VALUES(#{userId}, #{points}, NOW(), NOW())")
    int insertIfAbsent(ShopUserCurrency currency);

    @Select("SELECT * FROM t_shop_user_currency WHERE user_id = #{userId} LIMIT 1 FOR UPDATE")
    ShopUserCurrency selectForUpdate(@Param("userId") Long userId);

    @Update("UPDATE t_shop_user_currency SET points = points - #{amount}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND points >= #{amount}")
    int deductPoints(@Param("userId") Long userId, @Param("amount") Long amount);

    @Update("UPDATE t_shop_user_currency SET points = points + #{amount}, update_time = NOW() WHERE user_id = #{userId}")
    int addPoints(@Param("userId") Long userId, @Param("amount") Long amount);
}
