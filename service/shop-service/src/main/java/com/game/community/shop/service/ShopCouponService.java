package com.game.community.shop.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.entity.shop.ShopCoupon;
import com.game.community.model.vo.shop.ShopCouponVO;

public interface ShopCouponService {

    PageResult<ShopCoupon> listCoupons(Long page, Long size);

    ShopCoupon getCoupon(Long couponId);

    ShopCoupon saveCoupon(ShopCoupon coupon);

    ShopCoupon updateCoupon(ShopCoupon coupon);

    void deleteCoupon(Long couponId);

    PageResult<ShopCouponVO> listUserCoupons(Long userId, Long page, Long size, Integer status, Long itemId);

    ShopCouponVO getUserCoupon(Long userId, Long userCouponId);

    ShopCouponVO grantCoupon(Long userId, Long couponId);

    void deleteUserCoupon(Long userId, Long userCouponId);
}

