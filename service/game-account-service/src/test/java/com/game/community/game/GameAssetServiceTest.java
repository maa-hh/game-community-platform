package com.game.community.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.exception.BusinessException;
import com.game.community.game.mapper.AccountCharacterMapper;
import com.game.community.game.mapper.AccountItemMapper;
import com.game.community.game.mapper.AccountSkinMapper;
import com.game.community.game.mapper.GameCharacterMapper;
import com.game.community.game.mapper.GameItemMapper;
import com.game.community.game.mapper.GameSkinMapper;
import com.game.community.game.mapper.UserGameBindMapper;
import com.game.community.game.service.impl.GameAssetServiceImpl;
import com.game.community.model.base.PageResult;
import com.game.community.model.entity.gameaccount.AccountCharacter;
import com.game.community.model.entity.gameaccount.GameCharacter;
import com.game.community.model.entity.gameaccount.UserGameBind;
import com.game.community.model.vo.gameaccount.CharacterResourceVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameAssetServiceTest {

    @Mock
    private UserGameBindMapper userGameBindMapper;

    @Mock
    private GameCharacterMapper gameCharacterMapper;

    @Mock
    private GameSkinMapper gameSkinMapper;

    @Mock
    private GameItemMapper gameItemMapper;

    @Mock
    private AccountCharacterMapper accountCharacterMapper;

    @Mock
    private AccountSkinMapper accountSkinMapper;

    @Mock
    private AccountItemMapper accountItemMapper;

    @InjectMocks
    private GameAssetServiceImpl gameAssetService;

    @Test
    void listOwnedCharactersShouldFailWhenUserNotBound() {
        when(userGameBindMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> gameAssetService.listOwnedCharacters(8L, 1, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先绑定游戏账号");
    }

    @Test
    void listCharacterCatalogShouldMarkOwnedFlag() {
        UserGameBind bind = new UserGameBind();
        bind.setUserId(8L);
        bind.setGameAccountId(99L);
        when(userGameBindMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bind);

        when(gameCharacterMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenAnswer(invocation -> {
            Page<GameCharacter> page = invocation.getArgument(0);
            GameCharacter first = new GameCharacter();
            first.setId(1L);
            first.setCharacterCode("CHAR-001");
            first.setName("苍岚");
            first.setStatus(1);
            GameCharacter second = new GameCharacter();
            second.setId(2L);
            second.setCharacterCode("CHAR-002");
            second.setName("赤衡");
            second.setStatus(1);
            page.setRecords(List.of(first, second));
            page.setTotal(2L);
            return page;
        });

        AccountCharacter owned = new AccountCharacter();
        owned.setGameAccountId(99L);
        owned.setCharacterId(1L);
        owned.setCharacterCode("CHAR-001");
        owned.setStatus(1);
        when(accountCharacterMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(owned));

        PageResult<CharacterResourceVO> result = gameAssetService.listCharacterCatalog(8L, 1, 10, null, null);

        assertThat(result.getData()).hasSize(2);
        assertThat(result.getData().get(0).getOwned()).isTrue();
        assertThat(result.getData().get(1).getOwned()).isFalse();
    }
}
