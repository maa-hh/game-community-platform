package com.game.community.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.shop.ShopUserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShopUserCouponMapper extends BaseMapper<ShopUserCoupon> {

    @Update("UPDATE t_shop_user_coupon SET status = 3, order_no = #{orderNo} " +
            "WHERE id = #{userCouponId} AND user_id = #{userId} AND status = 0")
    int lockCoupon(@Param("userCouponId") Long userCouponId,
                   @Param("userId") Long userId,
                   @Param("orderNo") String orderNo);

    @Update("UPDATE t_shop_user_coupon SET status = 0, order_no = NULL " +
            "WHERE id = #{userCouponId} AND user_id = #{userId} AND status = 3 AND order_no = #{orderNo}")
    int unlockCoupon(@Param("userCouponId") Long userCouponId,
                     @Param("userId") Long userId,
                     @Param("orderNo") String orderNo);

    @Update("UPDATE t_shop_user_coupon SET status = 1, use_time = NOW() " +
            "WHERE id = #{userCouponId} AND user_id = #{userId} AND status = 3 AND order_no = #{orderNo}")
    int markUsed(@Param("userCouponId") Long userCouponId,
                 @Param("userId") Long userId,
                 @Param("orderNo") String orderNo);

    @Update("UPDATE t_shop_user_coupon SET status = 0, use_time = NULL, order_no = #{orderNo} " +
            "WHERE id = #{userCouponId} AND user_id = #{userId}")
    int resetCoupon(@Param("userCouponId") Long userCouponId,
                    @Param("userId") Long userId,
                    @Param("orderNo") String orderNo);
}
