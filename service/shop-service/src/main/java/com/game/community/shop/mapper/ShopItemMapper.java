package com.game.community.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.shop.ShopItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShopItemMapper extends BaseMapper<ShopItem> {

    @Update("UPDATE t_shop_item SET stock = stock - #{quantity}, version = version + 1, update_time = NOW() " +
            "WHERE id = #{itemId} AND stock >= #{quantity} AND stock >= 0")
    int deductLimitedStock(@Param("itemId") Long itemId, @Param("quantity") Integer quantity);

    @Update("UPDATE t_shop_item SET stock = stock + #{quantity}, version = version + 1, update_time = NOW() " +
            "WHERE id = #{itemId} AND stock >= 0")
    int restoreLimitedStock(@Param("itemId") Long itemId, @Param("quantity") Integer quantity);

    @Select("SELECT COALESCE(SUM(quantity), 0) FROM t_shop_order WHERE item_id = #{itemId} AND status = 1")
    int countCreatingQuantity(@Param("itemId") Long itemId);
}
