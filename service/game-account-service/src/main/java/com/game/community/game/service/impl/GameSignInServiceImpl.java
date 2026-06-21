package com.game.community.game.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.game.community.game.service.GameSignInService;
import com.game.community.model.entity.gameaccount.AccountCharacter;
import com.game.community.model.entity.gameaccount.AccountItem;
import com.game.community.model.entity.gameaccount.AccountSkin;
import com.game.community.model.entity.gameaccount.GameAccount;
import com.game.community.model.entity.gameaccount.GameCharacter;
import com.game.community.model.entity.gameaccount.GameItem;
import com.game.community.model.entity.gameaccount.GameSkin;
import com.game.community.model.entity.gameaccount.SignInRecord;
import com.game.community.model.entity.gameaccount.SignInReward;
import com.game.community.model.entity.gameaccount.UserGameBind;
import com.game.community.model.vo.gameaccount.SignInResultVO;
import com.game.community.model.vo.gameaccount.SignInStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GameSignInServiceImpl implements GameSignInService {

    private static final int ENABLED_STATUS = 1;
    private static final int REWARD_CHARACTER = 1;
    private static final int REWARD_SKIN = 2;
    private static final int REWARD_ITEM = 3;
    private static final int REWARD_GOLD = 4;
    private static final int REWARD_DIAMOND = 5;

    private final UserGameBindMapper userGameBindMapper;
    private final SignInRecordMapper signInRecordMapper;
    private final SignInRewardMapper signInRewardMapper;
    private final GameAccountMapper gameAccountMapper;
    private final GameCharacterMapper gameCharacterMapper;
    private final GameSkinMapper gameSkinMapper;
    private final GameItemMapper gameItemMapper;
    private final AccountCharacterMapper accountCharacterMapper;
    private final AccountSkinMapper accountSkinMapper;
    private final AccountItemMapper accountItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SignInResultVO signIn(Long userId) {
        Long gameAccountId = requireBoundGameAccountId(userId);
        LocalDate today = LocalDate.now();
        String yearMonth = YearMonth.from(today).toString();
        int dayIndex = today.getDayOfMonth();
        long todayMask = 1L << (dayIndex - 1);

        SignInRecord record = lockCurrentMonthRecord(gameAccountId, yearMonth);
        if (record != null && (safeSignBits(record) & todayMask) != 0) {
            throw new BusinessException("今天已签到");
        }

        SignInReward reward = requireReward(dayIndex);
        applyReward(gameAccountId, reward);

        SignInRecord finalRecord;
        if (record == null) {
            finalRecord = buildInitialRecord(gameAccountId, yearMonth, todayMask, today);
            try {
                signInRecordMapper.insert(finalRecord);
            } catch (DuplicateKeyException e) {
                finalRecord = lockCurrentMonthRecord(gameAccountId, yearMonth);
                if (finalRecord == null || (safeSignBits(finalRecord) & todayMask) != 0) {
                    throw new BusinessException("今天已签到");
                }
                mutateExistingRecord(finalRecord, todayMask, today);
                signInRecordMapper.updateById(finalRecord);
            }
        } else {
            mutateExistingRecord(record, todayMask, today);
            signInRecordMapper.updateById(record);
            finalRecord = record;
        }

        return toResultVO(finalRecord, reward, today);
    }

    @Override
    public SignInStatusVO getStatus(Long userId) {
        Long gameAccountId = requireBoundGameAccountId(userId);
        LocalDate today = LocalDate.now();
        String yearMonth = YearMonth.from(today).toString();
        SignInRecord record = signInRecordMapper.selectOne(new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getGameAccountId, gameAccountId)
                .eq(SignInRecord::getYearMonth, yearMonth)
                .last("limit 1"));
        SignInStatusVO vo = new SignInStatusVO();
        vo.setYearMonth(yearMonth);
        if (record == null) {
            vo.setSignedToday(false);
            vo.setSignCount(0);
            vo.setConsecutiveDays(0);
            vo.setSignBits(0L);
            vo.setSignedDays(List.of());
            return vo;
        }
        long bits = safeSignBits(record);
        vo.setSignedToday((bits & (1L << (today.getDayOfMonth() - 1))) != 0);
        vo.setSignCount(defaultZero(record.getSignCount()));
        vo.setConsecutiveDays(defaultZero(record.getConsecutiveDays()));
        vo.setSignBits(bits);
        vo.setLastSignInDate(record.getLastSignInDate());
        vo.setSignedDays(toSignedDays(bits, today.lengthOfMonth()));
        return vo;
    }

    private Long requireBoundGameAccountId(Long userId) {
        UserGameBind bind = userGameBindMapper.selectOne(new LambdaQueryWrapper<UserGameBind>()
                .eq(UserGameBind::getUserId, userId)
                .last("limit 1"));
        if (bind == null || bind.getGameAccountId() == null) {
            throw new BusinessException("请先绑定游戏账号");
        }
        return bind.getGameAccountId();
    }

    private SignInRecord lockCurrentMonthRecord(Long gameAccountId, String yearMonth) {
        return signInRecordMapper.selectOne(new LambdaQueryWrapper<SignInRecord>()
                .eq(SignInRecord::getGameAccountId, gameAccountId)
                .eq(SignInRecord::getYearMonth, yearMonth)
                .last("limit 1 for update"));
    }

    private SignInReward requireReward(int dayIndex) {
        SignInReward reward = signInRewardMapper.selectOne(new LambdaQueryWrapper<SignInReward>()
                .eq(SignInReward::getDayIndex, dayIndex)
                .eq(SignInReward::getStatus, ENABLED_STATUS)
                .last("limit 1"));
        if (reward == null) {
            throw new BusinessException("今日签到奖励未配置");
        }
        return reward;
    }

    private void applyReward(Long gameAccountId, SignInReward reward) {
        switch (reward.getRewardType()) {
            case REWARD_CHARACTER -> grantCharacter(gameAccountId, reward.getRewardCode());
            case REWARD_SKIN -> grantSkin(gameAccountId, reward.getRewardCode());
            case REWARD_ITEM -> grantItem(gameAccountId, reward.getRewardCode(), defaultQuantity(reward.getQuantity()));
            case REWARD_GOLD -> incrementGold(gameAccountId, defaultQuantity(reward.getQuantity()));
            case REWARD_DIAMOND -> incrementDiamond(gameAccountId, defaultQuantity(reward.getQuantity()));
            default -> throw new BusinessException("未知的签到奖励类型");
        }
    }

    private void grantCharacter(Long gameAccountId, String characterCode) {
        GameCharacter character = gameCharacterMapper.selectOne(new LambdaQueryWrapper<GameCharacter>()
                .eq(GameCharacter::getCharacterCode, characterCode)
                .eq(GameCharacter::getStatus, ENABLED_STATUS)
                .last("limit 1"));
        if (character == null) {
            throw new BusinessException("签到奖励角色不存在");
        }
        AccountCharacter record = new AccountCharacter();
        record.setGameAccountId(gameAccountId);
        record.setCharacterId(character.getId());
        record.setCharacterCode(character.getCharacterCode());
        record.setLevel(1);
        record.setExp(0);
        record.setBreakthroughLevel(0);
        record.setObtainTime(LocalDateTime.now());
        record.setStatus(ENABLED_STATUS);
        record.setVersion(0);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        try {
            accountCharacterMapper.insert(record);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private void grantSkin(Long gameAccountId, String skinCode) {
        GameSkin skin = gameSkinMapper.selectOne(new LambdaQueryWrapper<GameSkin>()
                .eq(GameSkin::getSkinCode, skinCode)
                .eq(GameSkin::getStatus, ENABLED_STATUS)
                .last("limit 1"));
        if (skin == null) {
            throw new BusinessException("签到奖励皮肤不存在");
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
        try {
            accountSkinMapper.insert(record);
        } catch (DuplicateKeyException ignored) {
        }
    }

    private void grantItem(Long gameAccountId, String itemCode, int quantity) {
        GameItem item = gameItemMapper.selectOne(new LambdaQueryWrapper<GameItem>()
                .eq(GameItem::getItemCode, itemCode)
                .eq(GameItem::getStatus, ENABLED_STATUS)
                .last("limit 1"));
        if (item == null) {
            throw new BusinessException("签到奖励道具不存在");
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

    private void incrementGold(Long gameAccountId, int quantity) {
        requireGameAccount(gameAccountId);
        gameAccountMapper.update(null, new UpdateWrapper<GameAccount>()
                .eq("id", gameAccountId)
                .setSql("gold = COALESCE(gold, 0) + " + quantity)
                .set("update_time", LocalDateTime.now()));
    }

    private void incrementDiamond(Long gameAccountId, int quantity) {
        requireGameAccount(gameAccountId);
        gameAccountMapper.update(null, new UpdateWrapper<GameAccount>()
                .eq("id", gameAccountId)
                .setSql("diamond = COALESCE(diamond, 0) + " + quantity)
                .set("update_time", LocalDateTime.now()));
    }

    private GameAccount requireGameAccount(Long gameAccountId) {
        GameAccount account = gameAccountMapper.selectById(gameAccountId);
        if (account == null) {
            throw new BusinessException("游戏账号不存在");
        }
        return account;
    }

    private SignInRecord buildInitialRecord(Long gameAccountId, String yearMonth, long signBits, LocalDate today) {
        SignInRecord record = new SignInRecord();
        record.setGameAccountId(gameAccountId);
        record.setYearMonth(yearMonth);
        record.setSignBits(signBits);
        record.setSignCount(1);
        record.setConsecutiveDays(1);
        record.setLastSignInDate(today.atStartOfDay());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }

    private void mutateExistingRecord(SignInRecord record, long todayMask, LocalDate today) {
        long currentBits = safeSignBits(record);
        record.setSignBits(currentBits | todayMask);
        record.setSignCount(defaultZero(record.getSignCount()) + 1);
        record.setConsecutiveDays(calculateConsecutiveDays(record.getLastSignInDate(), defaultZero(record.getConsecutiveDays()), today));
        record.setLastSignInDate(today.atStartOfDay());
        record.setUpdateTime(LocalDateTime.now());
    }

    private int calculateConsecutiveDays(LocalDateTime lastSignInDate, int previousConsecutiveDays, LocalDate today) {
        if (lastSignInDate == null) {
            return 1;
        }
        LocalDate lastDate = lastSignInDate.toLocalDate();
        if (lastDate.plusDays(1).equals(today)) {
            return previousConsecutiveDays + 1;
        }
        return 1;
    }

    private SignInResultVO toResultVO(SignInRecord record, SignInReward reward, LocalDate today) {
        SignInResultVO vo = new SignInResultVO();
        vo.setSigned(true);
        vo.setYearMonth(record.getYearMonth());
        vo.setDayIndex(today.getDayOfMonth());
        vo.setSignCount(record.getSignCount());
        vo.setConsecutiveDays(record.getConsecutiveDays());
        vo.setRewardName(reward.getRewardName());
        vo.setRewardType(reward.getRewardType());
        vo.setRewardCode(reward.getRewardCode());
        vo.setQuantity(defaultQuantity(reward.getQuantity()));
        vo.setSignTime(record.getLastSignInDate());
        return vo;
    }

    private long safeSignBits(SignInRecord record) {
        return record.getSignBits() == null ? 0L : record.getSignBits();
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private int defaultQuantity(Integer value) {
        return value == null || value <= 0 ? 1 : value;
    }

    private List<Integer> toSignedDays(long signBits, int daysOfMonth) {
        List<Integer> signedDays = new ArrayList<>();
        for (int i = 0; i < daysOfMonth; i++) {
            if ((signBits & (1L << i)) != 0) {
                signedDays.add(i + 1);
            }
        }
        return signedDays;
    }
}
