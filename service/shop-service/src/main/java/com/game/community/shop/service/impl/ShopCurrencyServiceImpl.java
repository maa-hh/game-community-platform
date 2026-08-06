package com.game.community.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.shop.ShopConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.entity.shop.ShopPointsLedger;
import com.game.community.model.entity.shop.ShopUserCurrency;
import com.game.community.model.vo.shop.ShopCurrencyVO;
import com.game.community.shop.mapper.ShopPointsLedgerMapper;
import com.game.community.shop.mapper.ShopUserCurrencyMapper;
import com.game.community.shop.service.ShopCurrencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ShopCurrencyServiceImpl implements ShopCurrencyService {

    private final ShopUserCurrencyMapper currencyMapper;
    private final ShopPointsLedgerMapper ledgerMapper;

    @Override
    public ShopCurrencyVO getOrCreate(Long userId) {
        validateUserId(userId);
        ShopUserCurrency currency = new ShopUserCurrency();
        currency.setUserId(userId);
        currency.setPoints(ShopConstants.DEFAULT_POINTS);
        currencyMapper.insertIfAbsent(currency);
        currency = currencyMapper.selectOne(new LambdaQueryWrapper<ShopUserCurrency>()
                .eq(ShopUserCurrency::getUserId, userId));
        return toVO(currency);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopCurrencyVO addPoints(Long userId, Long amount, String bizType, String bizRef, String remark) {
        validateAmount(amount);
        validateUserId(userId);
        validateBizKey(bizType, bizRef);
        ensureCurrency(userId);
        if (StringUtils.hasText(bizRef) && ledgerMapper.selectByBizRef(bizType, bizRef) != null) {
            return getOrCreate(userId);
        }
        currencyMapper.addPoints(userId, amount);
        ShopUserCurrency currency = currencyMapper.selectOne(new LambdaQueryWrapper<ShopUserCurrency>()
                .eq(ShopUserCurrency::getUserId, userId));
        writeLedger(userId, amount, currency.getPoints(), bizType, bizRef, remark);
        return toVO(currency);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopCurrencyVO deductPoints(Long userId, Long amount, String bizType, String bizRef, String remark) {
        validateAmount(amount);
        validateUserId(userId);
        validateBizKey(bizType, bizRef);
        ensureCurrency(userId);
        if (StringUtils.hasText(bizRef) && ledgerMapper.selectByBizRef(bizType, bizRef) != null) {
            return getOrCreate(userId);
        }
        if (currencyMapper.deductPoints(userId, amount) == 0) {
            throw new BusinessException("积分不足");
        }
        ShopUserCurrency currency = currencyMapper.selectOne(new LambdaQueryWrapper<ShopUserCurrency>()
                .eq(ShopUserCurrency::getUserId, userId));
        writeLedger(userId, -amount, currency.getPoints(), bizType, bizRef, remark);
        return toVO(currency);
    }

    private void ensureCurrency(Long userId) {
        getOrCreate(userId);
    }

    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException("积分数量必须大于0");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("用户ID不合法");
        }
    }

    private void validateBizKey(String bizType, String bizRef) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizRef)) {
            throw new BusinessException("积分业务流水标识不能为空");
        }
    }

    private void writeLedger(Long userId, long delta, long balanceAfter, String bizType, String bizRef, String remark) {
        ShopPointsLedger ledger = new ShopPointsLedger();
        ledger.setUserId(userId);
        ledger.setDelta(delta);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setBizType(bizType);
        ledger.setBizRef(bizRef);
        ledger.setRemark(StringUtils.hasText(remark) ? remark : ShopConstants.EMPTY_TEXT);
        ledger.setCreateTime(LocalDateTime.now());
        ledgerMapper.insert(ledger);
    }

    private ShopCurrencyVO toVO(ShopUserCurrency currency) {
        ShopCurrencyVO vo = new ShopCurrencyVO();
        vo.setPoints(currency.getPoints());
        return vo;
    }
}
