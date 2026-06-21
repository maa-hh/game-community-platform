package com.game.community.shop.service;

import com.game.community.model.vo.shop.ShopCurrencyVO;

public interface ShopCurrencyService {

    ShopCurrencyVO getOrCreate(Long userId);

    ShopCurrencyVO saveCurrency(Long userId, Long gold, Long diamond);

    ShopCurrencyVO resetCurrency(Long userId);

    ShopCurrencyVO addGold(Long userId, Long amount);

    ShopCurrencyVO addDiamond(Long userId, Long amount);

    ShopCurrencyVO deductGold(Long userId, Long amount);

    ShopCurrencyVO deductDiamond(Long userId, Long amount);

    void deleteCurrency(Long userId);
}
