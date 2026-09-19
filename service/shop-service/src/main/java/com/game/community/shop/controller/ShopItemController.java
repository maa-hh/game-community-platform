package com.game.community.shop.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.Result;
import com.game.community.model.dto.shop.SaveShopItemDTO;
import com.game.community.model.dto.shop.ShopItemPageQueryDTO;
import com.game.community.model.dto.shop.UpdateShopItemStatusDTO;
import com.game.community.model.vo.shop.ShopItemVO;
import com.game.community.shop.service.ShopItemService;
import com.game.community.shop.sentinel.ShopSentinelBlockHandler;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop/item")
@RequiredArgsConstructor
public class ShopItemController {

    private final ShopItemService itemService;

    @SentinelResource(value = "shop.item.page", blockHandlerClass = ShopSentinelBlockHandler.class,
            blockHandler = "handle")
    @GetMapping("/page")
    public Object pageItems(@Valid ShopItemPageQueryDTO query) {
        return itemService.pageItems(UserThreadLocal.getUserId(), query.getPage(), query.getSize(), query.getStatus());
    }

    @LoginCheck
    @GetMapping("/{itemId}")
    public Result<ShopItemVO> getItem(@PathVariable("itemId") Long itemId) {
        return Result.success(itemService.getItem(UserThreadLocal.getUserId(), itemId));
    }

    @AdminCheck
    @PostMapping
    public Result<ShopItemVO> saveItem(@Valid @RequestBody SaveShopItemDTO dto) {
        return Result.success(itemService.saveItem(dto));
    }

    @AdminCheck
    @PutMapping("/{itemId}/status")
    public Result<Void> updateStatus(@PathVariable("itemId") Long itemId,
                                     @Valid @RequestBody UpdateShopItemStatusDTO dto) {
        itemService.updateStatus(itemId, dto.getStatus());
        return Result.success(null);
    }

}
