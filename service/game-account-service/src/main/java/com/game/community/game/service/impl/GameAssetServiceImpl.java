package com.game.community.game.service.impl;

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
import com.game.community.game.service.GameAssetService;
import com.game.community.model.base.PageResult;
import com.game.community.model.entity.gameaccount.AccountCharacter;
import com.game.community.model.entity.gameaccount.AccountItem;
import com.game.community.model.entity.gameaccount.AccountSkin;
import com.game.community.model.entity.gameaccount.GameCharacter;
import com.game.community.model.entity.gameaccount.GameItem;
import com.game.community.model.entity.gameaccount.GameSkin;
import com.game.community.model.entity.gameaccount.UserGameBind;
import com.game.community.model.vo.gameaccount.CharacterResourceVO;
import com.game.community.model.vo.gameaccount.ItemResourceVO;
import com.game.community.model.vo.gameaccount.OwnedCharacterVO;
import com.game.community.model.vo.gameaccount.OwnedItemVO;
import com.game.community.model.vo.gameaccount.OwnedSkinVO;
import com.game.community.model.vo.gameaccount.SkinResourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameAssetServiceImpl implements GameAssetService {

    private static final int ACTIVE_STATUS = 1;

    private final UserGameBindMapper userGameBindMapper;
    private final GameCharacterMapper gameCharacterMapper;
    private final GameSkinMapper gameSkinMapper;
    private final GameItemMapper gameItemMapper;
    private final AccountCharacterMapper accountCharacterMapper;
    private final AccountSkinMapper accountSkinMapper;
    private final AccountItemMapper accountItemMapper;

    @Override
    public PageResult<OwnedCharacterVO> listOwnedCharacters(Long userId, int page, int size) {
        Long gameAccountId = requireBoundGameAccountId(userId);
        Page<AccountCharacter> ownedPage = accountCharacterMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<AccountCharacter>()
                        .eq(AccountCharacter::getGameAccountId, gameAccountId)
                        .eq(AccountCharacter::getStatus, ACTIVE_STATUS)
                        .orderByDesc(AccountCharacter::getObtainTime)
                        .orderByDesc(AccountCharacter::getId)
        );
        Map<Long, GameCharacter> characterMap = loadCharacterMapByIds(
                ownedPage.getRecords().stream().map(AccountCharacter::getCharacterId).toList()
        );
        List<OwnedCharacterVO> records = ownedPage.getRecords().stream()
                .map(record -> toOwnedCharacterVO(record, characterMap.get(record.getCharacterId())))
                .toList();
        return PageResult.of(records, ownedPage.getCurrent(), ownedPage.getSize(), ownedPage.getTotal());
    }

    @Override
    public PageResult<OwnedSkinVO> listOwnedSkins(Long userId, int page, int size) {
        Long gameAccountId = requireBoundGameAccountId(userId);
        Page<AccountSkin> ownedPage = accountSkinMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<AccountSkin>()
                        .eq(AccountSkin::getGameAccountId, gameAccountId)
                        .eq(AccountSkin::getStatus, ACTIVE_STATUS)
                        .orderByDesc(AccountSkin::getObtainTime)
                        .orderByDesc(AccountSkin::getId)
        );
        Map<Long, GameSkin> skinMap = loadSkinMapByIds(
                ownedPage.getRecords().stream().map(AccountSkin::getSkinId).toList()
        );
        List<OwnedSkinVO> records = ownedPage.getRecords().stream()
                .map(record -> toOwnedSkinVO(record, skinMap.get(record.getSkinId())))
                .toList();
        return PageResult.of(records, ownedPage.getCurrent(), ownedPage.getSize(), ownedPage.getTotal());
    }

    @Override
    public PageResult<OwnedItemVO> listOwnedItems(Long userId, int page, int size) {
        Long gameAccountId = requireBoundGameAccountId(userId);
        Page<AccountItem> ownedPage = accountItemMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<AccountItem>()
                        .eq(AccountItem::getGameAccountId, gameAccountId)
                        .eq(AccountItem::getStatus, ACTIVE_STATUS)
                        .gt(AccountItem::getQuantity, 0)
                        .orderByDesc(AccountItem::getLastObtainTime)
                        .orderByDesc(AccountItem::getId)
        );
        Map<Long, GameItem> itemMap = loadItemMapByIds(
                ownedPage.getRecords().stream().map(AccountItem::getItemId).toList()
        );
        List<OwnedItemVO> records = ownedPage.getRecords().stream()
                .map(record -> toOwnedItemVO(record, itemMap.get(record.getItemId())))
                .toList();
        return PageResult.of(records, ownedPage.getCurrent(), ownedPage.getSize(), ownedPage.getTotal());
    }

    @Override
    public PageResult<CharacterResourceVO> listCharacterCatalog(Long userId, int page, int size, String keyword, Integer rarity) {
        Long gameAccountId = findBoundGameAccountId(userId);
        Page<GameCharacter> characterPage = gameCharacterMapper.selectPage(
                new Page<>(page, size),
                buildCharacterQuery(keyword, rarity)
        );
        Set<Long> ownedIds = gameAccountId == null
                ? Collections.emptySet()
                : accountCharacterMapper.selectList(
                                new LambdaQueryWrapper<AccountCharacter>()
                                        .eq(AccountCharacter::getGameAccountId, gameAccountId)
                                        .in(!characterPage.getRecords().isEmpty(), AccountCharacter::getCharacterId,
                                                characterPage.getRecords().stream().map(GameCharacter::getId).toList())
                        ).stream()
                        .map(AccountCharacter::getCharacterId)
                        .collect(Collectors.toSet());
        List<CharacterResourceVO> records = characterPage.getRecords().stream()
                .map(character -> toCharacterResourceVO(character, ownedIds.contains(character.getId())))
                .toList();
        return PageResult.of(records, characterPage.getCurrent(), characterPage.getSize(), characterPage.getTotal());
    }

    @Override
    public PageResult<SkinResourceVO> listSkinCatalog(Long userId, int page, int size, String keyword, Long characterId, Integer rarity) {
        Long gameAccountId = findBoundGameAccountId(userId);
        Page<GameSkin> skinPage = gameSkinMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<GameSkin>()
                        .eq(GameSkin::getStatus, ACTIVE_STATUS)
                        .eq(characterId != null, GameSkin::getCharacterId, characterId)
                        .eq(rarity != null, GameSkin::getRarity, rarity)
                        .and(StringUtils.hasText(keyword),
                                wrapper -> wrapper.like(GameSkin::getName, keyword).or().like(GameSkin::getSkinCode, keyword))
                        .orderByDesc(GameSkin::getSortOrder)
                        .orderByDesc(GameSkin::getId)
        );
        Map<Long, AccountSkin> ownedMap = gameAccountId == null
                ? Collections.emptyMap()
                : accountSkinMapper.selectList(
                                new LambdaQueryWrapper<AccountSkin>()
                                        .eq(AccountSkin::getGameAccountId, gameAccountId)
                                        .in(!skinPage.getRecords().isEmpty(), AccountSkin::getSkinId,
                                                skinPage.getRecords().stream().map(GameSkin::getId).toList())
                        ).stream()
                        .collect(Collectors.toMap(AccountSkin::getSkinId, Function.identity(), (left, right) -> left));
        List<SkinResourceVO> records = skinPage.getRecords().stream()
                .map(skin -> toSkinResourceVO(skin, ownedMap.get(skin.getId())))
                .toList();
        return PageResult.of(records, skinPage.getCurrent(), skinPage.getSize(), skinPage.getTotal());
    }

    @Override
    public PageResult<ItemResourceVO> listItemCatalog(Long userId, int page, int size, String keyword, Integer itemType) {
        Long gameAccountId = findBoundGameAccountId(userId);
        Page<GameItem> itemPage = gameItemMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<GameItem>()
                        .eq(GameItem::getStatus, ACTIVE_STATUS)
                        .eq(itemType != null, GameItem::getItemType, itemType)
                        .and(StringUtils.hasText(keyword),
                                wrapper -> wrapper.like(GameItem::getName, keyword).or().like(GameItem::getItemCode, keyword))
                        .orderByDesc(GameItem::getSortOrder)
                        .orderByDesc(GameItem::getId)
        );
        Map<Long, AccountItem> ownedMap = gameAccountId == null
                ? Collections.emptyMap()
                : accountItemMapper.selectList(
                                new LambdaQueryWrapper<AccountItem>()
                                        .eq(AccountItem::getGameAccountId, gameAccountId)
                                        .in(!itemPage.getRecords().isEmpty(), AccountItem::getItemId,
                                                itemPage.getRecords().stream().map(GameItem::getId).toList())
                        ).stream()
                        .collect(Collectors.toMap(AccountItem::getItemId, Function.identity(), (left, right) -> left));
        List<ItemResourceVO> records = itemPage.getRecords().stream()
                .map(item -> toItemResourceVO(item, ownedMap.get(item.getId())))
                .toList();
        return PageResult.of(records, itemPage.getCurrent(), itemPage.getSize(), itemPage.getTotal());
    }

    private Long requireBoundGameAccountId(Long userId) {
        Long gameAccountId = findBoundGameAccountId(userId);
        if (gameAccountId == null) {
            throw new BusinessException("请先绑定游戏账号");
        }
        return gameAccountId;
    }

    private Long findBoundGameAccountId(Long userId) {
        UserGameBind bind = userGameBindMapper.selectOne(new LambdaQueryWrapper<UserGameBind>()
                .eq(UserGameBind::getUserId, userId)
                .last("limit 1"));
        return bind == null ? null : bind.getGameAccountId();
    }

    private LambdaQueryWrapper<GameCharacter> buildCharacterQuery(String keyword, Integer rarity) {
        return new LambdaQueryWrapper<GameCharacter>()
                .eq(GameCharacter::getStatus, ACTIVE_STATUS)
                .eq(rarity != null, GameCharacter::getRarity, rarity)
                .and(StringUtils.hasText(keyword),
                        wrapper -> wrapper.like(GameCharacter::getName, keyword)
                                .or().like(GameCharacter::getCharacterCode, keyword)
                                .or().like(GameCharacter::getTitle, keyword))
                .orderByDesc(GameCharacter::getSortOrder)
                .orderByDesc(GameCharacter::getId);
    }

    private Map<Long, GameCharacter> loadCharacterMapByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return gameCharacterMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(GameCharacter::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, GameSkin> loadSkinMapByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return gameSkinMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(GameSkin::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, GameItem> loadItemMapByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return gameItemMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(GameItem::getId, Function.identity(), (left, right) -> left));
    }

    private OwnedCharacterVO toOwnedCharacterVO(AccountCharacter record, GameCharacter character) {
        OwnedCharacterVO vo = new OwnedCharacterVO();
        vo.setCharacterId(record.getCharacterId());
        vo.setCharacterCode(record.getCharacterCode());
        vo.setLevel(record.getLevel());
        vo.setStatus(record.getStatus());
        vo.setObtainTime(record.getObtainTime());
        if (character != null) {
            vo.setName(character.getName());
            vo.setIcon(character.getIcon());
            vo.setRarity(character.getRarity());
        }
        return vo;
    }

    private OwnedSkinVO toOwnedSkinVO(AccountSkin record, GameSkin skin) {
        OwnedSkinVO vo = new OwnedSkinVO();
        vo.setSkinId(record.getSkinId());
        vo.setSkinCode(record.getSkinCode());
        vo.setCharacterId(record.getCharacterId());
        vo.setCharacterCode(record.getCharacterCode());
        vo.setEquipStatus(record.getEquipStatus());
        vo.setStatus(record.getStatus());
        vo.setObtainTime(record.getObtainTime());
        if (skin != null) {
            vo.setName(skin.getName());
            vo.setIcon(skin.getIcon());
            vo.setRarity(skin.getRarity());
        }
        return vo;
    }

    private OwnedItemVO toOwnedItemVO(AccountItem record, GameItem item) {
        OwnedItemVO vo = new OwnedItemVO();
        vo.setItemId(record.getItemId());
        vo.setItemCode(record.getItemCode());
        vo.setQuantity(record.getQuantity());
        vo.setStatus(record.getStatus());
        vo.setLastObtainTime(record.getLastObtainTime());
        if (item != null) {
            vo.setName(item.getName());
            vo.setIcon(item.getIcon());
            vo.setItemType(item.getItemType());
            vo.setRarity(item.getRarity());
        }
        return vo;
    }

    private CharacterResourceVO toCharacterResourceVO(GameCharacter character, boolean owned) {
        CharacterResourceVO vo = new CharacterResourceVO();
        vo.setCharacterId(character.getId());
        vo.setCharacterCode(character.getCharacterCode());
        vo.setName(character.getName());
        vo.setTitle(character.getTitle());
        vo.setIcon(character.getIcon());
        vo.setRarity(character.getRarity());
        vo.setElementType(character.getElementType());
        vo.setCharacterType(character.getCharacterType());
        vo.setStatus(character.getStatus());
        vo.setOwned(owned);
        return vo;
    }

    private SkinResourceVO toSkinResourceVO(GameSkin skin, AccountSkin ownedRecord) {
        SkinResourceVO vo = new SkinResourceVO();
        vo.setSkinId(skin.getId());
        vo.setSkinCode(skin.getSkinCode());
        vo.setCharacterId(skin.getCharacterId());
        vo.setCharacterCode(skin.getCharacterCode());
        vo.setName(skin.getName());
        vo.setIcon(skin.getIcon());
        vo.setRarity(skin.getRarity());
        vo.setStatus(skin.getStatus());
        vo.setOwned(ownedRecord != null);
        vo.setEquipStatus(ownedRecord != null ? ownedRecord.getEquipStatus() : 0);
        return vo;
    }

    private ItemResourceVO toItemResourceVO(GameItem item, AccountItem ownedRecord) {
        ItemResourceVO vo = new ItemResourceVO();
        vo.setItemId(item.getId());
        vo.setItemCode(item.getItemCode());
        vo.setName(item.getName());
        vo.setItemType(item.getItemType());
        vo.setRarity(item.getRarity());
        vo.setIcon(item.getIcon());
        vo.setMaxStackCount(item.getMaxStackCount());
        vo.setStatus(item.getStatus());
        vo.setOwned(ownedRecord != null && ownedRecord.getQuantity() != null && ownedRecord.getQuantity() > 0);
        vo.setQuantity(ownedRecord != null ? ownedRecord.getQuantity() : 0);
        return vo;
    }
}
