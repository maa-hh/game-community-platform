package com.game.community.shop.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.Result;
import com.game.community.model.vo.shop.ShopCurrencyVO;
import com.game.community.shop.service.ShopCurrencyService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/shop/currency", "/api/shop/user-currency"})
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
    @PostMapping("/save")
    public Result<ShopCurrencyVO> saveCurrency(@RequestParam("userId") Long userId,
                                               @RequestParam(value = "gold", required = false) Long gold,
                                               @RequestParam(value = "diamond", required = false) Long diamond) {
        return Result.success(currencyService.saveCurrency(userId, gold, diamond));
    }

    @AdminCheck
    @PostMapping("/reset")
    public Result<ShopCurrencyVO> resetCurrency(@RequestParam("userId") Long userId) {
        return Result.success(currencyService.resetCurrency(userId));
    }

    @LoginCheck
    @PostMapping("/add-gold")
    public Result<ShopCurrencyVO> addGold(@RequestParam(value = "userId", required = false) Long userId,
                                          @RequestParam("amount") Long amount) {
        Long targetUserId = userId == null ? UserThreadLocal.getUserId() : userId;
        return Result.success(currencyService.addGold(targetUserId, amount));
    }

    @LoginCheck
    @PostMapping("/add-diamond")
    public Result<ShopCurrencyVO> addDiamond(@RequestParam(value = "userId", required = false) Long userId,
                                             @RequestParam("amount") Long amount) {
        Long targetUserId = userId == null ? UserThreadLocal.getUserId() : userId;
        return Result.success(currencyService.addDiamond(targetUserId, amount));
    }

    @AdminCheck
    @PostMapping("/deduct-gold")
    public Result<ShopCurrencyVO> deductGold(@RequestParam("userId") Long userId,
                                             @RequestParam("amount") Long amount) {
        return Result.success(currencyService.deductGold(userId, amount));
    }

    @AdminCheck
    @PostMapping("/deduct-diamond")
    public Result<ShopCurrencyVO> deductDiamond(@RequestParam("userId") Long userId,
                                                @RequestParam("amount") Long amount) {
        return Result.success(currencyService.deductDiamond(userId, amount));
    }

    @AdminCheck
    @DeleteMapping("/{userId}")
    public Result<Void> deleteCurrency(@PathVariable("userId") Long userId) {
        currencyService.deleteCurrency(userId);
        return Result.success(null);
    }
}
