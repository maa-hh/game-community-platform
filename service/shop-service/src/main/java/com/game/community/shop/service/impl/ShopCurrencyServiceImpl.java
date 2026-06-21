package com.game.community.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.entity.shop.ShopUserCurrency;
import com.game.community.model.vo.shop.ShopCurrencyVO;
import com.game.community.shop.mapper.ShopUserCurrencyMapper;
import com.game.community.shop.service.ShopCurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ShopCurrencyServiceImpl implements ShopCurrencyService {

    private final ShopUserCurrencyMapper currencyMapper;

    @Override
    public ShopCurrencyVO getOrCreate(Long userId) {
        ShopUserCurrency currency = currencyMapper.selectOne(new LambdaQueryWrapper<ShopUserCurrency>()
                .eq(ShopUserCurrency::getUserId, userId));
        if (currency == null) {
            currency = new ShopUserCurrency();
            currency.setUserId(userId);
            currency.setGold(1000L);
            currency.setDiamond(500L);
            currency.setCreateTime(LocalDateTime.now());
            currency.setUpdateTime(LocalDateTime.now());
            currencyMapper.insert(currency);
        }
        return toVO(currency);
    }

    @Override
    public ShopCurrencyVO saveCurrency(Long userId, Long gold, Long diamond) {
        if (userId == null) {
            throw new BusinessException("用户ID不能为空");
        }
        ShopUserCurrency currency = currencyMapper.selectOne(new LambdaQueryWrapper<ShopUserCurrency>()
                .eq(ShopUserCurrency::getUserId, userId));
        if (currency == null) {
            currency = new ShopUserCurrency();
            currency.setUserId(userId);
            currency.setCreateTime(LocalDateTime.now());
        }
        currency.setGold(gold == null ? 0L : Math.max(0L, gold));
        currency.setDiamond(diamond == null ? 0L : Math.max(0L, diamond));
        currency.setUpdateTime(LocalDateTime.now());
        if (currency.getId() == null) {
            currencyMapper.insert(currency);
        } else {
            currencyMapper.updateById(currency);
        }
        return toVO(currencyMapper.selectOne(new LambdaQueryWrapper<ShopUserCurrency>().eq(ShopUserCurrency::getUserId, userId)));
    }

    @Override
    public ShopCurrencyVO resetCurrency(Long userId) {
        return saveCurrency(userId, 0L, 0L);
    }

    @Override
    public ShopCurrencyVO addGold(Long userId, Long amount) {
        validateAmount(amount);
        ensureCurrency(userId);
        currencyMapper.addGold(userId, amount);
        return getOrCreate(userId);
    }

    @Override
    public ShopCurrencyVO addDiamond(Long userId, Long amount) {
        validateAmount(amount);
        ensureCurrency(userId);
        currencyMapper.addDiamond(userId, amount);
        return getOrCreate(userId);
    }

    @Override
    public ShopCurrencyVO deductGold(Long userId, Long amount) {
        validateAmount(amount);
        ensureCurrency(userId);
        if (currencyMapper.deductGold(userId, amount) == 0) {
            throw new BusinessException("金币不足");
        }
        return getOrCreate(userId);
    }

    @Override
    public ShopCurrencyVO deductDiamond(Long userId, Long amount) {
        validateAmount(amount);
        ensureCurrency(userId);
        if (currencyMapper.deductDiamond(userId, amount) == 0) {
            throw new BusinessException("钻石不足");
        }
        return getOrCreate(userId);
    }

    @Override
    public void deleteCurrency(Long userId) {
        currencyMapper.delete(new LambdaQueryWrapper<ShopUserCurrency>().eq(ShopUserCurrency::getUserId, userId));
    }

    private void ensureCurrency(Long userId) {
        getOrCreate(userId);
    }

    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException("金额必须大于0");
        }
    }

    private ShopCurrencyVO toVO(ShopUserCurrency currency) {
        ShopCurrencyVO vo = new ShopCurrencyVO();
        vo.setGold(currency.getGold());
        vo.setDiamond(currency.getDiamond());
        return vo;
    }
}
