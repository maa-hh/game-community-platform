package com.game.community.game;

import com.game.community.model.dto.gameaccount.GameAccountBindDTO;
import com.game.community.model.entity.gameaccount.AccountCharacter;
import com.game.community.model.entity.gameaccount.GameAccount;
import com.game.community.model.entity.gameaccount.GameCharacter;
import com.game.community.model.entity.gameaccount.GameDeliveryRecord;
import com.game.community.model.entity.gameaccount.SignInRecord;
import com.game.community.model.entity.gameaccount.SignInReward;
import com.game.community.model.entity.gameaccount.UserGameBind;
import com.game.community.model.mongo.CharacterDetail;
import com.game.community.model.vo.gameaccount.GameAccountAssetsVO;
import com.game.community.model.vo.gameaccount.GameAccountBindVO;
import com.game.community.model.vo.gameaccount.GameAccountProfileVO;
import com.game.community.model.vo.gameaccount.OwnedCharacterVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModelContractSmokeTest {

    @Test
    void shouldInstantiateGameAccountContracts() {
        LocalDateTime now = LocalDateTime.now();

        GameAccountBindDTO bindDTO = new GameAccountBindDTO();
        bindDTO.setAccountNo("GA-2001");

        GameAccount account = new GameAccount();
        account.setId(2001L);
        account.setAccountNo(bindDTO.getAccountNo());
        account.setName("Ranger");
        account.setLevel(18);
        account.setGold(8800L);
        account.setDiamond(320L);
        account.setCurrentSeasonRank("PLATINUM");
        account.setHistorySeasonRank("DIAMOND");
        account.setStatus(1);
        account.setCreateTime(now);

        UserGameBind bind = new UserGameBind();
        bind.setId(3001L);
        bind.setUserId(1001L);
        bind.setGameAccountId(account.getId());
        bind.setBindTime(now);
        bind.setCreateTime(now);

        GameCharacter gameCharacter = new GameCharacter();
        gameCharacter.setId(4001L);
        gameCharacter.setCharacterCode("char-001");
        gameCharacter.setName("Sentinel");
        gameCharacter.setRarity(5);

        CharacterDetail characterDetail = new CharacterDetail();
        characterDetail.setCharacterCode(gameCharacter.getCharacterCode());
        characterDetail.setStory("Frontline guardian");

        AccountCharacter accountCharacter = new AccountCharacter();
        accountCharacter.setId(5001L);
        accountCharacter.setGameAccountId(account.getId());
        accountCharacter.setCharacterId(gameCharacter.getId());
        accountCharacter.setCharacterCode(gameCharacter.getCharacterCode());
        accountCharacter.setLevel(10);

        SignInReward reward = new SignInReward();
        reward.setId(6001L);
        reward.setDayIndex(1);
        reward.setRewardType(3);
        reward.setBusinessCode("SIGN_IN");
        reward.setRewardCode("GOLD");
        reward.setQuantity(100);

        SignInRecord record = new SignInRecord();
        record.setId(6002L);
        record.setGameAccountId(account.getId());
        record.setYearMonth("2026-05");
        record.setSignBits(31L);
        record.setSignCount(5);
        record.setConsecutiveDays(3);
        record.setLastSignInDate(now);

        GameDeliveryRecord deliveryRecord = new GameDeliveryRecord();
        deliveryRecord.setId(8001L);
        deliveryRecord.setOrderNo("ORDER-8001");
        deliveryRecord.setUserId(bind.getUserId());
        deliveryRecord.setGameAccountId(account.getId());
        deliveryRecord.setProductType(2);
        deliveryRecord.setBusinessCode("SHOP_ORDER");
        deliveryRecord.setBusinessId(91001L);
        deliveryRecord.setQuantity(1);
        deliveryRecord.setStatus(1);

        OwnedCharacterVO ownedCharacterVO = new OwnedCharacterVO();
        ownedCharacterVO.setCharacterId(gameCharacter.getId());
        ownedCharacterVO.setCharacterCode(gameCharacter.getCharacterCode());
        ownedCharacterVO.setName(gameCharacter.getName());
        ownedCharacterVO.setLevel(accountCharacter.getLevel());

        GameAccountAssetsVO assetsVO = new GameAccountAssetsVO();
        assetsVO.setCharacters(List.of(ownedCharacterVO));

        GameAccountProfileVO profileVO = new GameAccountProfileVO();
        profileVO.setGameAccountId(account.getId());
        profileVO.setAccountNo(account.getAccountNo());
        profileVO.setName(account.getName());
        profileVO.setLevel(account.getLevel());

        GameAccountBindVO bindVO = new GameAccountBindVO();
        bindVO.setBindId(bind.getId());
        bindVO.setGameAccountId(account.getId());
        bindVO.setAccountNo(account.getAccountNo());
        bindVO.setName(account.getName());
        bindVO.setBindTime(bind.getBindTime());

        assertEquals("GA-2001", bindDTO.getAccountNo());
        assertEquals("Sentinel", ownedCharacterVO.getName());
        assertEquals(1, assetsVO.getCharacters().size());
        assertEquals("Ranger", profileVO.getName());
        assertEquals(bind.getId(), bindVO.getBindId());
        assertEquals("Frontline guardian", characterDetail.getStory());
        assertNotNull(record.getLastSignInDate());
        assertEquals("SHOP_ORDER", deliveryRecord.getBusinessCode());
    }
}
