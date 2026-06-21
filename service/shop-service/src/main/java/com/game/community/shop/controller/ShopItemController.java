package com.game.community.shop.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.shop.SaveShopItemDTO;
import com.game.community.model.vo.shop.ShopItemVO;
import com.game.community.shop.service.ShopItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/shop/item", "/api/shop/item"})
@RequiredArgsConstructor
public class ShopItemController {

    private final ShopItemService itemService;

    @LoginCheck
    @GetMapping("/page")
    public PageResult<ShopItemVO> pageItems(@RequestParam(value = "page", defaultValue = "1") Long page,
                                            @RequestParam(value = "size", defaultValue = "12") Long size,
                                            @RequestParam(value = "productType", required = false) Integer productType,
                                            @RequestParam(value = "status", required = false) Integer status) {
        return itemService.pageItems(page, size, productType, status);
    }

    @LoginCheck
    @GetMapping("/list")
    public PageResult<ShopItemVO> listItems(@RequestParam(value = "current", defaultValue = "1") Long current,
                                            @RequestParam(value = "page", required = false) Long page,
                                            @RequestParam(value = "size", defaultValue = "12") Long size,
                                            @RequestParam(value = "productType", required = false) Integer productType,
                                            @RequestParam(value = "status", required = false) Integer status) {
        return itemService.pageItems(page == null ? current : page, size, productType, status);
    }

    @LoginCheck
    @GetMapping("/{itemId}")
    public Result<ShopItemVO> getItem(@PathVariable("itemId") Long itemId) {
        return Result.success(itemService.getItem(itemId));
    }

    @AdminCheck
    @PostMapping
    public Result<ShopItemVO> saveItem(@Valid @RequestBody SaveShopItemDTO dto) {
        return Result.success(itemService.saveItem(dto));
    }

    @AdminCheck
    @PostMapping("/save")
    public Result<ShopItemVO> saveItemCompat(@Valid @RequestBody SaveShopItemDTO dto) {
        return Result.success(itemService.saveItem(dto));
    }

    @AdminCheck
    @PutMapping("/{itemId}/status")
    public Result<Void> updateStatus(@PathVariable("itemId") Long itemId,
                                     @RequestParam("status") Integer status) {
        itemService.updateStatus(itemId, status);
        return Result.success(null);
    }

    @AdminCheck
    @PostMapping("/status")
    public Result<Void> updateStatusCompat(@RequestParam("itemId") Long itemId,
                                           @RequestParam("status") Integer status) {
        itemService.updateStatus(itemId, status);
        return Result.success(null);
    }

    @AdminCheck
    @PostMapping("/onShelf/{itemId}")
    public Result<Void> onShelf(@PathVariable("itemId") Long itemId) {
        itemService.updateStatus(itemId, 1);
        return Result.success(null);
    }

    @AdminCheck
    @PostMapping("/offShelf/{itemId}")
    public Result<Void> offShelf(@PathVariable("itemId") Long itemId) {
        itemService.updateStatus(itemId, 0);
        return Result.success(null);
    }
}
