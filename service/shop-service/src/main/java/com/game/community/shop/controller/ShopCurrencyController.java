package com.game.community.shop.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.common.constant.shop.ShopConstants;
import com.game.community.model.base.Result;
import com.game.community.model.vo.shop.ShopCurrencyVO;
import com.game.community.shop.service.ShopCurrencyService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/shop/currency")
@RequiredArgsConstructor
public class ShopCurrencyController {

    private final ShopCurrencyService currencyService;

    @LoginCheck
    @GetMapping("/me")
    public Result<ShopCurrencyVO> getMine() {
        return Result.success(currencyService.getOrCreate(UserThreadLocal.getUserId()));
    }

    @AdminCheck
    @GetMapping("/{userId}")
    public Result<ShopCurrencyVO> getUserCurrency(@PathVariable("userId") Long userId) {
        return Result.success(currencyService.getOrCreate(userId));
    }

    @AdminCheck
    @PostMapping("/add")
    public Result<ShopCurrencyVO> addPoints(@RequestParam("userId") Long userId,
                                            @RequestParam("amount") Long amount,
                                            @RequestParam(value = "remark", defaultValue = "") String remark) {
        return Result.success(currencyService.addPoints(userId, amount,
                ShopConstants.PointsBizType.ADMIN_ADJUST, "ADMIN-ADD-" + UUID.randomUUID(), remark));
    }

    @AdminCheck
    @PostMapping("/deduct")
    public Result<ShopCurrencyVO> deductPoints(@RequestParam("userId") Long userId,
                                               @RequestParam("amount") Long amount,
                                               @RequestParam(value = "remark", defaultValue = "") String remark) {
        return Result.success(currencyService.deductPoints(userId, amount,
                ShopConstants.PointsBizType.ADMIN_ADJUST, "ADMIN-DEDUCT-" + UUID.randomUUID(), remark));
    }
}
