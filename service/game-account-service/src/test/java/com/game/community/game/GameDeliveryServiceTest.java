package com.game.community.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.shop.ShopConstants;
import com.game.community.game.mapper.AccountItemMapper;
import com.game.community.game.mapper.AccountSkinMapper;
import com.game.community.game.mapper.GameDeliveryRecordMapper;
import com.game.community.game.mapper.GameItemMapper;
import com.game.community.game.mapper.GameSkinMapper;
import com.game.community.game.mapper.UserGameBindMapper;
import com.game.community.game.service.impl.GameDeliveryServiceImpl;
import com.game.community.model.entity.gameaccount.GameDeliveryRecord;
import com.game.community.model.entity.gameaccount.GameSkin;
import com.game.community.model.entity.gameaccount.UserGameBind;
import com.game.community.model.message.ShopOrderPaidMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameDeliveryServiceTest {

    @Mock
    private GameDeliveryRecordMapper gameDeliveryRecordMapper;

    @Mock
    private UserGameBindMapper userGameBindMapper;

    @Mock
    private GameSkinMapper gameSkinMapper;

    @Mock
    private GameItemMapper gameItemMapper;

    @Mock
    private AccountSkinMapper accountSkinMapper;

    @Mock
    private AccountItemMapper accountItemMapper;

    @InjectMocks
    private GameDeliveryServiceImpl gameDeliveryService;

    @Test
    void deliverShouldIgnoreDuplicateOrder() {
        ShopOrderPaidMessage message = new ShopOrderPaidMessage();
        message.setOrderNo("ORDER-1");
        when(gameDeliveryRecordMapper.insert(any(GameDeliveryRecord.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        gameDeliveryService.deliver(message);

        verify(userGameBindMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void deliverShouldGrantSkinAndMarkSuccess() {
        ShopOrderPaidMessage message = new ShopOrderPaidMessage();
        message.setOrderNo("ORDER-2");
        message.setUserId(8L);
        message.setProductType(ShopConstants.PRODUCT_TYPE_SKIN);
        message.setBusinessCode("SKIN-001");
        message.setQuantity(1);

        UserGameBind bind = new UserGameBind();
        bind.setUserId(8L);
        bind.setGameAccountId(99L);
        when(userGameBindMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bind);

        GameSkin skin = new GameSkin();
        skin.setId(3L);
        skin.setCharacterId(1L);
        skin.setCharacterCode("CHAR-001");
        skin.setSkinCode("SKIN-001");
        when(gameSkinMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(skin);

        gameDeliveryService.deliver(message);

        verify(accountSkinMapper).insert((com.game.community.model.entity.gameaccount.AccountSkin) argThat((com.game.community.model.entity.gameaccount.AccountSkin record) ->
                record.getGameAccountId().equals(99L) && record.getSkinId().equals(3L)
        ));
        verify(gameDeliveryRecordMapper).updateById((GameDeliveryRecord) argThat((GameDeliveryRecord record) ->
                record.getStatus() == 1 && "ORDER-2".equals(record.getOrderNo())
        ));
    }
}
