package com.game.community.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.shop.ShopPurchaseLimit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface ShopPurchaseLimitMapper extends BaseMapper<ShopPurchaseLimit> {

    @Select("SELECT * FROM t_shop_purchase_limit WHERE user_id = #{userId} AND item_id = #{itemId} LIMIT 1 FOR UPDATE")
    ShopPurchaseLimit selectForUpdate(@Param("userId") Long userId, @Param("itemId") Long itemId);

    @Insert("INSERT IGNORE INTO t_shop_purchase_limit(user_id, item_id, purchased_count, reserved_count, "
            + "last_purchase_at, window_start_at, create_time, update_time) "
            + "VALUES(#{userId}, #{itemId}, 0, 0, #{lastPurchaseAt}, #{windowStartAt}, NOW(), NOW())")
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("itemId") Long itemId,
                       @Param("lastPurchaseAt") LocalDateTime lastPurchaseAt,
                       @Param("windowStartAt") LocalDateTime windowStartAt);

    @Select("SELECT * FROM t_shop_purchase_limit WHERE user_id = #{userId} AND item_id = #{itemId} LIMIT 1")
    ShopPurchaseLimit selectByUserAndItem(@Param("userId") Long userId, @Param("itemId") Long itemId);
}
