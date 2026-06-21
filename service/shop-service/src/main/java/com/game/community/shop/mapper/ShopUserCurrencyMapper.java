package com.game.community.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.shop.ShopUserCurrency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShopUserCurrencyMapper extends BaseMapper<ShopUserCurrency> {

    @Update("UPDATE t_shop_user_currency SET gold = gold - #{amount}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND gold >= #{amount}")
    int deductGold(@Param("userId") Long userId, @Param("amount") Long amount);

    @Update("UPDATE t_shop_user_currency SET diamond = diamond - #{amount}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND diamond >= #{amount}")
    int deductDiamond(@Param("userId") Long userId, @Param("amount") Long amount);

    @Update("UPDATE t_shop_user_currency SET gold = gold + #{amount}, update_time = NOW() WHERE user_id = #{userId}")
    int addGold(@Param("userId") Long userId, @Param("amount") Long amount);

    @Update("UPDATE t_shop_user_currency SET diamond = diamond + #{amount}, update_time = NOW() WHERE user_id = #{userId}")
    int addDiamond(@Param("userId") Long userId, @Param("amount") Long amount);
}
