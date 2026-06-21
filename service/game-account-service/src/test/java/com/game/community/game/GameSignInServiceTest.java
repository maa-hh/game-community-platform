package com.game.community.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.game.community.common.exception.BusinessException;
import com.game.community.game.mapper.AccountCharacterMapper;
import com.game.community.game.mapper.AccountItemMapper;
import com.game.community.game.mapper.AccountSkinMapper;
import com.game.community.game.mapper.GameAccountMapper;
import com.game.community.game.mapper.GameCharacterMapper;
import com.game.community.game.mapper.GameItemMapper;
import com.game.community.game.mapper.GameSkinMapper;
import com.game.community.game.mapper.SignInRecordMapper;
import com.game.community.game.mapper.SignInRewardMapper;
import com.game.community.game.mapper.UserGameBindMapper;
import com.game.community.game.service.impl.GameSignInServiceImpl;
import com.game.community.model.entity.gameaccount.GameAccount;
import com.game.community.model.entity.gameaccount.SignInRecord;
import com.game.community.model.entity.gameaccount.SignInReward;
import com.game.community.model.entity.gameaccount.UserGameBind;
import com.game.community.model.vo.gameaccount.SignInResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameSignInServiceTest {

    @Mock
    private UserGameBindMapper userGameBindMapper;

    @Mock
    private SignInRecordMapper signInRecordMapper;

    @Mock
    private SignInRewardMapper signInRewardMapper;

    @Mock
    private GameAccountMapper gameAccountMapper;

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
    private GameSignInServiceImpl gameSignInService;

    @Test
    void signInShouldCreateMonthlyRecordAndRewardGold() {
        UserGameBind bind = new UserGameBind();
        bind.setUserId(8L);
        bind.setGameAccountId(99L);
        when(userGameBindMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bind);
        when(signInRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        SignInReward reward = new SignInReward();
        reward.setDayIndex(LocalDate.now().getDayOfMonth());
        reward.setRewardType(4);
        reward.setRewardCode("gold");
        reward.setRewardName("金币 x100");
        reward.setQuantity(100);
        reward.setStatus(1);
        when(signInRewardMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(reward);

        GameAccount account = new GameAccount();
        account.setId(99L);
        account.setGold(200L);
        when(gameAccountMapper.selectById(99L)).thenReturn(account);

        SignInResultVO result = gameSignInService.signIn(8L);

        assertThat(result.getSigned()).isTrue();
        assertThat(result.getRewardName()).isEqualTo("金币 x100");
        assertThat(result.getSignCount()).isEqualTo(1);
        verify(signInRecordMapper).insert(any(SignInRecord.class));
        verify(gameAccountMapper).update(eq(null), any(UpdateWrapper.class));
    }

    @Test
    void signInShouldRejectRepeatedSignInSameDay() {
        UserGameBind bind = new UserGameBind();
        bind.setUserId(8L);
        bind.setGameAccountId(99L);
        when(userGameBindMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(bind);

        int offset = LocalDate.now().getDayOfMonth() - 1;
        SignInRecord record = new SignInRecord();
        record.setId(1L);
        record.setGameAccountId(99L);
        record.setSignBits(1L << offset);
        record.setSignCount(1);
        when(signInRecordMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(record);

        assertThatThrownBy(() -> gameSignInService.signIn(8L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("今天已签到");
    }
}
