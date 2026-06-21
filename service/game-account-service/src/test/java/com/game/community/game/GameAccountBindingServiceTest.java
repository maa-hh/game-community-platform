package com.game.community.game;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.exception.BusinessException;
import com.game.community.game.mapper.GameAccountMapper;
import com.game.community.game.mapper.UserGameBindMapper;
import com.game.community.game.service.impl.GameAccountBindingServiceImpl;
import com.game.community.model.dto.gameaccount.GameAccountBindDTO;
import com.game.community.model.entity.gameaccount.GameAccount;
import com.game.community.model.entity.gameaccount.UserGameBind;
import com.game.community.model.vo.gameaccount.GameAccountBindVO;
import com.game.community.model.vo.gameaccount.GameAccountProfileVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameAccountBindingServiceTest {

    @Mock
    private GameAccountMapper gameAccountMapper;

    @Mock
    private UserGameBindMapper userGameBindMapper;

    @InjectMocks
    private GameAccountBindingServiceImpl bindingService;

    @Test
    void bindShouldFailWhenAccountDoesNotExist() {
        GameAccountBindDTO dto = new GameAccountBindDTO();
        dto.setAccountNo("GA99999");
        when(gameAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> bindingService.bind(4L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("游戏账号不存在");
    }

    @Test
    void bindShouldCreateRelationWhenUserHasNoBinding() {
        GameAccount account = new GameAccount();
        account.setId(10L);
        account.setAccountNo("GA10001");
        account.setName("星野");
        when(gameAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(account);
        when(userGameBindMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        GameAccountBindDTO dto = new GameAccountBindDTO();
        dto.setAccountNo("GA10001");
        GameAccountBindVO bindVO = bindingService.bind(4L, dto);

        assertThat(bindVO.getGameAccountId()).isEqualTo(10L);
        assertThat(bindVO.getAccountNo()).isEqualTo("GA10001");
        verify(userGameBindMapper).insert(any(UserGameBind.class));
    }

    @Test
    void rebindShouldFailWhenTargetAccountAlreadyBound() {
        UserGameBind current = new UserGameBind();
        current.setId(1L);
        current.setUserId(4L);
        current.setGameAccountId(10L);
        GameAccount target = new GameAccount();
        target.setId(11L);
        target.setAccountNo("GA10002");
        when(userGameBindMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(current);
        when(gameAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(target);
        doThrow(new DuplicateKeyException("dup")).when(userGameBindMapper).updateById(any(UserGameBind.class));

        GameAccountBindDTO dto = new GameAccountBindDTO();
        dto.setAccountNo("GA10002");

        assertThatThrownBy(() -> bindingService.rebind(4L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他用户绑定");
    }

    @Test
    void currentShouldReturnUnboundProfileWhenNoBindingExists() {
        when(userGameBindMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        GameAccountProfileVO profileVO = bindingService.current(4L);

        assertThat(profileVO.getBound()).isFalse();
        assertThat(profileVO.getGameAccountId()).isNull();
    }
}
