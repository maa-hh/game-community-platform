package com.game.community.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.shop.ShopOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShopOrderMapper extends BaseMapper<ShopOrder> {

    @Select("SELECT * FROM t_shop_order WHERE order_no = #{orderNo}")
    ShopOrder selectByOrderNo(@Param("orderNo") String orderNo);

    @Update("UPDATE t_shop_order SET status = #{nextStatus}, update_time = NOW() " +
            "WHERE order_no = #{orderNo} AND user_id = #{userId} AND status = #{currentStatus}")
    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("userId") Long userId,
                     @Param("currentStatus") Integer currentStatus,
                     @Param("nextStatus") Integer nextStatus);

    @Update("UPDATE t_shop_order SET status = #{nextStatus}, pay_time = NOW(), update_time = NOW() " +
            "WHERE order_no = #{orderNo} AND user_id = #{userId} AND status = #{currentStatus}")
    int markPaid(@Param("orderNo") String orderNo,
                 @Param("userId") Long userId,
                 @Param("currentStatus") Integer currentStatus,
                 @Param("nextStatus") Integer nextStatus);

    @Select("SELECT COALESCE(SUM(quantity), 0) FROM t_shop_order " +
            "WHERE user_id = #{userId} AND item_id = #{itemId} AND status IN (2, 3, 4)")
    int countActivePurchasedQuantity(@Param("userId") Long userId, @Param("itemId") Long itemId);
}
