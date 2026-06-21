package com.game.community.game.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.exception.BusinessException;
import com.game.community.game.mapper.GameAccountMapper;
import com.game.community.game.mapper.UserGameBindMapper;
import com.game.community.game.service.GameAccountBindingService;
import com.game.community.model.dto.gameaccount.GameAccountBindDTO;
import com.game.community.model.entity.gameaccount.GameAccount;
import com.game.community.model.entity.gameaccount.UserGameBind;
import com.game.community.model.vo.gameaccount.GameAccountBindVO;
import com.game.community.model.vo.gameaccount.GameAccountProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GameAccountBindingServiceImpl implements GameAccountBindingService {

    private final GameAccountMapper gameAccountMapper;
    private final UserGameBindMapper userGameBindMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GameAccountBindVO bind(Long userId, GameAccountBindDTO dto) {
        GameAccount account = requireActiveAccount(dto.getAccountNo());
        UserGameBind existingBind = findByUserId(userId);
        if (existingBind != null) {
            throw new BusinessException("您已绑定游戏账号，请使用换绑");
        }
        UserGameBind bind = new UserGameBind();
        bind.setUserId(userId);
        bind.setGameAccountId(account.getId());
        bind.setBindTime(LocalDateTime.now());
        try {
            userGameBindMapper.insert(bind);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该游戏账号已被其他用户绑定");
        }
        return toBindVO(bind, account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GameAccountBindVO rebind(Long userId, GameAccountBindDTO dto) {
        UserGameBind existingBind = findByUserId(userId);
        if (existingBind == null) {
            throw new BusinessException("当前用户还未绑定游戏账号");
        }
        GameAccount account = requireActiveAccount(dto.getAccountNo());
        if (account.getId().equals(existingBind.getGameAccountId())) {
            return toBindVO(existingBind, account);
        }
        existingBind.setGameAccountId(account.getId());
        existingBind.setBindTime(LocalDateTime.now());
        try {
            userGameBindMapper.updateById(existingBind);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("该游戏账号已被其他用户绑定");
        }
        return toBindVO(existingBind, account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long userId) {
        UserGameBind existingBind = findByUserId(userId);
        if (existingBind == null) {
            throw new BusinessException("当前用户还未绑定游戏账号");
        }
        userGameBindMapper.deleteById(existingBind.getId());
    }

    @Override
    public GameAccountProfileVO current(Long userId) {
        UserGameBind bind = findByUserId(userId);
        if (bind == null) {
            GameAccountProfileVO unbound = new GameAccountProfileVO();
            unbound.setBound(Boolean.FALSE);
            return unbound;
        }
        GameAccount account = gameAccountMapper.selectById(bind.getGameAccountId());
        if (account == null) {
            throw new BusinessException("绑定的游戏账号不存在");
        }
        GameAccountProfileVO profileVO = new GameAccountProfileVO();
        profileVO.setBound(Boolean.TRUE);
        profileVO.setGameAccountId(account.getId());
        profileVO.setAccountNo(account.getAccountNo());
        profileVO.setName(account.getName());
        profileVO.setLevel(account.getLevel());
        profileVO.setGold(account.getGold());
        profileVO.setDiamond(account.getDiamond());
        profileVO.setCurrentSeasonRank(account.getCurrentSeasonRank());
        profileVO.setHistorySeasonRank(account.getHistorySeasonRank());
        profileVO.setStatus(account.getStatus());
        profileVO.setUpdateTime(account.getUpdateTime());
        return profileVO;
    }

    private GameAccount requireActiveAccount(String accountNo) {
        GameAccount account = gameAccountMapper.selectOne(new LambdaQueryWrapper<GameAccount>()
                .eq(GameAccount::getAccountNo, accountNo)
                .last("limit 1"));
        if (account == null) {
            throw new BusinessException("游戏账号不存在");
        }
        if (account.getStatus() != null && account.getStatus() == 1) {
            throw new BusinessException("该游戏账号不可绑定");
        }
        return account;
    }

    private UserGameBind findByUserId(Long userId) {
        return userGameBindMapper.selectOne(new LambdaQueryWrapper<UserGameBind>()
                .eq(UserGameBind::getUserId, userId)
                .last("limit 1"));
    }

    private GameAccountBindVO toBindVO(UserGameBind bind, GameAccount account) {
        GameAccountBindVO bindVO = new GameAccountBindVO();
        bindVO.setBindId(bind.getId());
        bindVO.setGameAccountId(account.getId());
        bindVO.setAccountNo(account.getAccountNo());
        bindVO.setName(account.getName());
        bindVO.setBindTime(bind.getBindTime());
        bindVO.setCreateTime(bind.getCreateTime());
        return bindVO;
    }
}
