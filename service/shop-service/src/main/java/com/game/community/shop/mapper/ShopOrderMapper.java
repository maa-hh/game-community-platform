package com.game.community.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.shop.ShopOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShopOrderMapper extends BaseMapper<ShopOrder> {

    @Select("SELECT * FROM t_shop_order WHERE order_no = #{orderNo}")
    ShopOrder selectByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM t_shop_order WHERE order_no = #{orderNo} FOR UPDATE")
    ShopOrder selectByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM t_shop_order WHERE user_id = #{userId} AND request_id = #{requestId} LIMIT 1")
    ShopOrder selectByUserAndRequest(@Param("userId") Long userId, @Param("requestId") String requestId);

    @Select("SELECT * FROM t_shop_order WHERE status = 1 ORDER BY id LIMIT #{limit}")
    List<ShopOrder> selectCreatingOrders(@Param("limit") int limit);

    /**
     * Redis 过期队列丢失时的数据库兜底扫描，只处理已经明确过期的待支付订单。
     */
    @Select("SELECT * FROM t_shop_order WHERE status = 2 AND expire_time IS NOT NULL "
            + "AND expire_time <= NOW() ORDER BY expire_time, id LIMIT #{limit}")
    List<ShopOrder> selectExpiredPendingOrders(@Param("limit") int limit);

    @Select("SELECT COALESCE(SUM(quantity), 0) FROM t_shop_order "
            + "WHERE user_id = #{userId} AND item_id = #{itemId} AND status = 1")
    int countCreatingQuantity(@Param("userId") Long userId, @Param("itemId") Long itemId);

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

    @Update("UPDATE t_shop_order SET status = #{nextStatus}, pay_time = NOW(), complete_time = NOW(), update_time = NOW() "
            + "WHERE order_no = #{orderNo} AND user_id = #{userId} AND status = #{currentStatus}")
    int markCompleted(@Param("orderNo") String orderNo,
                      @Param("userId") Long userId,
                      @Param("currentStatus") Integer currentStatus,
                      @Param("nextStatus") Integer nextStatus);

    @Update("UPDATE t_shop_order SET status = #{nextStatus}, fail_reason = #{failReason}, update_time = NOW() "
            + "WHERE order_no = #{orderNo} AND status = #{currentStatus}")
    int markCreateFailed(@Param("orderNo") String orderNo,
                         @Param("currentStatus") Integer currentStatus,
                         @Param("nextStatus") Integer nextStatus,
                         @Param("failReason") String failReason);

}
