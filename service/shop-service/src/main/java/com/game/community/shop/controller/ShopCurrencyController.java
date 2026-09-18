package com.game.community.shop.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.common.constant.shop.ShopConstants;
import com.game.community.model.base.Result;
import com.game.community.model.dto.shop.AdminPointsAdjustDTO;
import com.game.community.model.vo.shop.ShopCurrencyVO;
import com.game.community.shop.service.ShopCurrencyService;
import com.game.community.shop.sentinel.ShopSentinelBlockHandler;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop/currency")
@RequiredArgsConstructor
public class ShopCurrencyController {

    private final ShopCurrencyService currencyService;

    @LoginCheck
    @SentinelResource(value = "shop.currency.read", blockHandlerClass = ShopSentinelBlockHandler.class,
            blockHandler = "handle")
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
    public Result<ShopCurrencyVO> addPoints(@Valid @RequestBody AdminPointsAdjustDTO dto) {
        return Result.success(currencyService.addPoints(dto.getUserId(), dto.getAmount(),
                ShopConstants.PointsBizType.ADMIN_ADJUST, "ADMIN-ADD-" + dto.getRequestId(),
                buildAuditRemark(dto.getRemark())));
    }

    @AdminCheck
    @PostMapping("/deduct")
    public Result<ShopCurrencyVO> deductPoints(@Valid @RequestBody AdminPointsAdjustDTO dto) {
        return Result.success(currencyService.deductPoints(dto.getUserId(), dto.getAmount(),
                ShopConstants.PointsBizType.ADMIN_ADJUST, "ADMIN-DEDUCT-" + dto.getRequestId(),
                buildAuditRemark(dto.getRemark())));
    }

    private String buildAuditRemark(String remark) {
        String operator = "operatorUserId=" + UserThreadLocal.getUserId();
        return operator + (remark == null || remark.isBlank() ? "" : "; " + remark);
    }
}
