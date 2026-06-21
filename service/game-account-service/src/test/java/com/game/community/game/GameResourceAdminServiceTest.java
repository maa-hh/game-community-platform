package com.game.community.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.exception.BusinessException;
import com.game.community.game.mapper.AccountCharacterMapper;
import com.game.community.game.mapper.AccountItemMapper;
import com.game.community.game.mapper.AccountSkinMapper;
import com.game.community.game.mapper.GameCharacterMapper;
import com.game.community.game.mapper.GameItemMapper;
import com.game.community.game.mapper.GameSkinMapper;
import com.game.community.game.mapper.SignInRewardMapper;
import com.game.community.game.mongo.CharacterDetailRepository;
import com.game.community.game.mongo.ItemDetailRepository;
import com.game.community.game.mongo.SkinDetailRepository;
import com.game.community.game.service.impl.GameResourceAdminServiceImpl;
import com.game.community.model.entity.gameaccount.GameCharacter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameResourceAdminServiceTest {

    @Mock
    private GameCharacterMapper gameCharacterMapper;

    @Mock
    private GameSkinMapper gameSkinMapper;

    @Mock
    private GameItemMapper gameItemMapper;

    @Mock
    private SignInRewardMapper signInRewardMapper;

    @Mock
    private AccountCharacterMapper accountCharacterMapper;

    @Mock
    private AccountSkinMapper accountSkinMapper;

    @Mock
    private AccountItemMapper accountItemMapper;

    @Mock
    private CharacterDetailRepository characterDetailRepository;

    @Mock
    private SkinDetailRepository skinDetailRepository;

    @Mock
    private ItemDetailRepository itemDetailRepository;

    @InjectMocks
    private GameResourceAdminServiceImpl gameResourceAdminService;

    @Test
    void createCharacterShouldRejectDuplicateCode() {
        GameCharacter character = new GameCharacter();
        character.setCharacterCode("CHAR-001");
        character.setName("苍岚");
        when(gameCharacterMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> gameResourceAdminService.createCharacter(character))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色编码已存在");
    }

    @Test
    void disableCharacterShouldSoftDisableWhenAlreadyOwned() {
        GameCharacter character = new GameCharacter();
        character.setId(1L);
        character.setCharacterCode("CHAR-001");
        character.setStatus(1);
        when(gameCharacterMapper.selectById(1L)).thenReturn(character);

        gameResourceAdminService.disableCharacter(1L);

        verify(gameCharacterMapper).updateById((GameCharacter) argThat((GameCharacter updated) ->
                updated.getId().equals(1L) && updated.getStatus() == 0
        ));
    }
}
