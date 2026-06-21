package com.game.community.shop.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.entity.shop.ShopCoupon;
import com.game.community.model.vo.shop.ShopCouponVO;
import com.game.community.shop.service.ShopCouponService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shop/coupon")
@RequiredArgsConstructor
public class ShopCouponController {

    private final ShopCouponService couponService;

    @AdminCheck
    @GetMapping("/list")
    public PageResult<ShopCoupon> listCoupons(@RequestParam(value = "current", defaultValue = "1") Long current,
                                              @RequestParam(value = "page", required = false) Long page,
                                              @RequestParam(value = "size", defaultValue = "10") Long size) {
        return couponService.listCoupons(page == null ? current : page, size);
    }

    @AdminCheck
    @GetMapping("/{couponId}")
    public Result<ShopCoupon> getCoupon(@PathVariable("couponId") Long couponId) {
        return Result.success(couponService.getCoupon(couponId));
    }

    @AdminCheck
    @PostMapping("/save")
    public Result<ShopCoupon> saveCoupon(@RequestBody ShopCoupon coupon) {
        return Result.success(couponService.saveCoupon(coupon));
    }

    @AdminCheck
    @PostMapping("/update")
    public Result<ShopCoupon> updateCoupon(@RequestBody ShopCoupon coupon) {
        return Result.success(couponService.updateCoupon(coupon));
    }

    @AdminCheck
    @DeleteMapping("/{couponId}")
    public Result<Void> deleteCoupon(@PathVariable("couponId") Long couponId) {
        couponService.deleteCoupon(couponId);
        return Result.success(null);
    }

    @AdminCheck
    @PostMapping("/grant")
    public Result<ShopCouponVO> grantCoupon(@RequestParam("userId") Long userId,
                                            @RequestParam("couponId") Long couponId) {
        return Result.success(couponService.grantCoupon(userId, couponId));
    }
}

