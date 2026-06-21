package com.game.community.shop.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.shop.SaveShopItemDTO;
import com.game.community.model.vo.shop.ShopItemVO;

public interface ShopItemService {

    PageResult<ShopItemVO> pageItems(Long page, Long size, Integer productType, Integer status);

    ShopItemVO getItem(Long itemId);

    ShopItemVO saveItem(SaveShopItemDTO dto);

    void updateStatus(Long itemId, Integer status);

    void syncStockToRedis(Long itemId);
}
