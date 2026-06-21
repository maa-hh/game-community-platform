package com.game.community.shop.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.vo.shop.ShopCouponVO;
import com.game.community.shop.service.ShopCouponService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shop/user-coupon")
@RequiredArgsConstructor
public class ShopUserCouponController {

    private final ShopCouponService couponService;

    @LoginCheck
    @GetMapping("/list")
    public PageResult<ShopCouponVO> listUserCoupons(@RequestParam(value = "current", defaultValue = "1") Long current,
                                                    @RequestParam(value = "page", required = false) Long page,
                                                    @RequestParam(value = "size", defaultValue = "10") Long size,
                                                    @RequestParam(value = "status", required = false) Integer status,
                                                    @RequestParam(value = "itemId", required = false) Long itemId) {
        return couponService.listUserCoupons(UserThreadLocal.getUserId(), page == null ? current : page, size, status, itemId);
    }

    @LoginCheck
    @GetMapping("/{userCouponId}")
    public Result<ShopCouponVO> getUserCoupon(@PathVariable("userCouponId") Long userCouponId) {
        return Result.success(couponService.getUserCoupon(UserThreadLocal.getUserId(), userCouponId));
    }

    @AdminCheck
    @DeleteMapping("/{userCouponId}")
    public Result<Void> deleteUserCoupon(@RequestParam("userId") Long userId,
                                         @PathVariable("userCouponId") Long userCouponId) {
        couponService.deleteUserCoupon(userId, userCouponId);
        return Result.success(null);
    }
}

