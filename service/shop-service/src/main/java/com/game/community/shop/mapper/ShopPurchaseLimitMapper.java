package com.game.community.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.shop.ShopPurchaseLimit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShopPurchaseLimitMapper extends BaseMapper<ShopPurchaseLimit> {

    @Insert("INSERT INTO t_shop_purchase_limit(user_id, item_id, purchased_count, create_time, update_time) " +
            "VALUES(#{userId}, #{itemId}, #{quantity}, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE purchased_count = purchased_count + #{quantity}, update_time = NOW()")
    int increase(@Param("userId") Long userId, @Param("itemId") Long itemId, @Param("quantity") Integer quantity);

    @Update("UPDATE t_shop_purchase_limit SET purchased_count = GREATEST(0, purchased_count - #{quantity}), update_time = NOW() " +
            "WHERE user_id = #{userId} AND item_id = #{itemId}")
    int decrease(@Param("userId") Long userId, @Param("itemId") Long itemId, @Param("quantity") Integer quantity);

    @Select("SELECT COALESCE(MAX(purchased_count), 0) FROM t_shop_purchase_limit " +
            "WHERE user_id = #{userId} AND item_id = #{itemId}")
    int selectPurchasedCount(@Param("userId") Long userId, @Param("itemId") Long itemId);
}
