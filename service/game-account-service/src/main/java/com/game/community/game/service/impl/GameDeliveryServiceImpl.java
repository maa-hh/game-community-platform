package com.game.community.game.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.shop.ShopConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.game.mapper.AccountItemMapper;
import com.game.community.game.mapper.AccountSkinMapper;
import com.game.community.game.mapper.GameDeliveryRecordMapper;
import com.game.community.game.mapper.GameItemMapper;
import com.game.community.game.mapper.GameSkinMapper;
import com.game.community.game.mapper.UserGameBindMapper;
import com.game.community.game.service.GameDeliveryService;
import com.game.community.model.entity.gameaccount.AccountItem;
import com.game.community.model.entity.gameaccount.AccountSkin;
import com.game.community.model.entity.gameaccount.GameDeliveryRecord;
import com.game.community.model.entity.gameaccount.GameItem;
import com.game.community.model.entity.gameaccount.GameSkin;
import com.game.community.model.entity.gameaccount.UserGameBind;
import com.game.community.model.message.ShopOrderPaidMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameDeliveryServiceImpl implements GameDeliveryService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_SUCCESS = 1;
    private static final int STATUS_FAILED = 2;
    private static final int ENABLED_STATUS = 1;

    private final GameDeliveryRecordMapper gameDeliveryRecordMapper;
    private final UserGameBindMapper userGameBindMapper;
    private final GameSkinMapper gameSkinMapper;
    private final GameItemMapper gameItemMapper;
    private final AccountSkinMapper accountSkinMapper;
    private final AccountItemMapper accountItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliver(ShopOrderPaidMessage message) {
        GameDeliveryRecord record = createPendingRecord(message);
        if (record == null) {
            return;
        }
        try {
            UserGameBind bind = userGameBindMapper.selectOne(new LambdaQueryWrapper<UserGameBind>()
                    .eq(UserGameBind::getUserId, message.getUserId())
                    .last("limit 1"));
            if (bind == null || bind.getGameAccountId() == null) {
                markFailed(record, "用户未绑定游戏账号");
                return;
            }
            record.setGameAccountId(bind.getGameAccountId());
            if (message.getProductType() == null) {
                markFailed(record, "商品类型不能为空");
                return;
            }
            switch (message.getProductType()) {
                case ShopConstants.PRODUCT_TYPE_COUPON -> markSuccess(record);
                case ShopConstants.PRODUCT_TYPE_SKIN -> {
                    grantSkin(bind.getGameAccountId(), message.getBusinessCode());
                    markSuccess(record);
                }
                case ShopConstants.PRODUCT_TYPE_ITEM -> {
                    grantItem(bind.getGameAccountId(), message.getBusinessCode(), defaultQuantity(message.getQuantity()));
                    markSuccess(record);
                }
                default -> markFailed(record, "暂不支持的商品类型");
            }
        } catch (BusinessException e) {
            markFailed(record, e.getMessage());
        } catch (DuplicateKeyException e) {
            markSuccess(record);
        } catch (RuntimeException e) {
            markFailed(record, "发货异常");
            throw e;
        }
    }

    private GameDeliveryRecord createPendingRecord(ShopOrderPaidMessage message) {
        GameDeliveryRecord record = new GameDeliveryRecord();
        record.setOrderNo(message.getOrderNo());
        record.setUserId(message.getUserId());
        record.setProductType(message.getProductType());
        record.setBusinessCode(message.getBusinessCode());
        record.setQuantity(defaultQuantity(message.getQuantity()));
        record.setStatus(STATUS_PENDING);
        record.setFailReason("");
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        try {
            gameDeliveryRecordMapper.insert(record);
            return record;
        } catch (DuplicateKeyException e) {
            log.info("订单已发货或已在处理中，跳过重复消息: orderNo={}", message.getOrderNo());
            return null;
        }
    }

    private void grantSkin(Long gameAccountId, String skinCode) {
        GameSkin skin = gameSkinMapper.selectOne(new LambdaQueryWrapper<GameSkin>()
                .eq(GameSkin::getSkinCode, skinCode)
                .eq(GameSkin::getStatus, ENABLED_STATUS)
                .last("limit 1"));
        if (skin == null) {
            throw new BusinessException("发货皮肤不存在");
        }
        AccountSkin record = new AccountSkin();
        record.setGameAccountId(gameAccountId);
        record.setSkinId(skin.getId());
        record.setCharacterId(skin.getCharacterId());
        record.setSkinCode(skin.getSkinCode());
        record.setCharacterCode(skin.getCharacterCode());
        record.setEquipStatus(0);
        record.setObtainTime(LocalDateTime.now());
        record.setStatus(ENABLED_STATUS);
        record.setVersion(0);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        accountSkinMapper.insert(record);
    }

    private void grantItem(Long gameAccountId, String itemCode, int quantity) {
        GameItem item = gameItemMapper.selectOne(new LambdaQueryWrapper<GameItem>()
                .eq(GameItem::getItemCode, itemCode)
                .eq(GameItem::getStatus, ENABLED_STATUS)
                .last("limit 1"));
        if (item == null) {
            throw new BusinessException("发货道具不存在");
        }
        AccountItem existing = accountItemMapper.selectOne(new LambdaQueryWrapper<AccountItem>()
                .eq(AccountItem::getGameAccountId, gameAccountId)
                .eq(AccountItem::getItemId, item.getId())
                .last("limit 1"));
        if (existing == null) {
            AccountItem record = new AccountItem();
            record.setGameAccountId(gameAccountId);
            record.setItemId(item.getId());
            record.setItemCode(item.getItemCode());
            record.setQuantity(quantity);
            record.setLastObtainTime(LocalDateTime.now());
            record.setStatus(ENABLED_STATUS);
            record.setVersion(0);
            record.setCreateTime(LocalDateTime.now());
            record.setUpdateTime(LocalDateTime.now());
            try {
                accountItemMapper.insert(record);
                return;
            } catch (DuplicateKeyException ignored) {
            }
        }
        accountItemMapper.update(null, new LambdaUpdateWrapper<AccountItem>()
                .eq(AccountItem::getGameAccountId, gameAccountId)
                .eq(AccountItem::getItemId, item.getId())
                .setSql("quantity = quantity + " + quantity)
                .set(AccountItem::getLastObtainTime, LocalDateTime.now())
                .set(AccountItem::getUpdateTime, LocalDateTime.now())
                .set(AccountItem::getStatus, ENABLED_STATUS));
    }

    private void markSuccess(GameDeliveryRecord record) {
        record.setStatus(STATUS_SUCCESS);
        record.setFailReason("");
        record.setUpdateTime(LocalDateTime.now());
        gameDeliveryRecordMapper.updateById(record);
    }

    private void markFailed(GameDeliveryRecord record, String reason) {
        record.setStatus(STATUS_FAILED);
        record.setFailReason(reason);
        record.setUpdateTime(LocalDateTime.now());
        gameDeliveryRecordMapper.updateById(record);
    }

    private int defaultQuantity(Integer quantity) {
        return quantity == null || quantity <= 0 ? 1 : quantity;
    }
}
