package com.game.community.shop.service;

import com.game.community.model.vo.shop.ShopCurrencyVO;

public interface ShopCurrencyService {

    ShopCurrencyVO getOrCreate(Long userId);

    ShopCurrencyVO addPoints(Long userId, Long amount, String bizType, String bizRef, String remark);

    ShopCurrencyVO deductPoints(Long userId, Long amount, String bizType, String bizRef, String remark);

}
