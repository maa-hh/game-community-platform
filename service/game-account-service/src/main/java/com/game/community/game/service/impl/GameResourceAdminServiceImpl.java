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
import com.game.community.game.mapper.SignInRewardMapper;
import com.game.community.game.mongo.CharacterDetailRepository;
import com.game.community.game.mongo.ItemDetailRepository;
import com.game.community.game.mongo.SkinDetailRepository;
import com.game.community.game.service.GameResourceAdminService;
import com.game.community.model.entity.gameaccount.AccountCharacter;
import com.game.community.model.entity.gameaccount.AccountItem;
import com.game.community.model.entity.gameaccount.AccountSkin;
import com.game.community.model.entity.gameaccount.GameCharacter;
import com.game.community.model.entity.gameaccount.GameItem;
import com.game.community.model.entity.gameaccount.GameSkin;
import com.game.community.model.entity.gameaccount.SignInReward;
import com.game.community.model.mongo.CharacterDetail;
import com.game.community.model.mongo.ItemDetail;
import com.game.community.model.mongo.SkinDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GameResourceAdminServiceImpl implements GameResourceAdminService {

    private static final int ACTIVE_STATUS = 1;
    private static final int DISABLED_STATUS = 0;

    private final GameCharacterMapper gameCharacterMapper;
    private final GameSkinMapper gameSkinMapper;
    private final GameItemMapper gameItemMapper;
    private final SignInRewardMapper signInRewardMapper;
    private final AccountCharacterMapper accountCharacterMapper;
    private final AccountSkinMapper accountSkinMapper;
    private final AccountItemMapper accountItemMapper;
    private final CharacterDetailRepository characterDetailRepository;
    private final SkinDetailRepository skinDetailRepository;
    private final ItemDetailRepository itemDetailRepository;

    @Override
    public Page<GameCharacter> pageCharacters(int page, int size, String keyword, Integer rarity, Integer status) {
        return gameCharacterMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<GameCharacter>()
                        .eq(status != null, GameCharacter::getStatus, status)
                        .eq(rarity != null, GameCharacter::getRarity, rarity)
                        .and(StringUtils.hasText(keyword),
                                wrapper -> wrapper.like(GameCharacter::getName, keyword)
                                        .or().like(GameCharacter::getCharacterCode, keyword)
                                        .or().like(GameCharacter::getTitle, keyword))
                        .orderByDesc(GameCharacter::getSortOrder)
                        .orderByDesc(GameCharacter::getId)
        );
    }

    @Override
    public Page<GameSkin> pageSkins(int page, int size, String keyword, Long characterId, Integer rarity, Integer status) {
        return gameSkinMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<GameSkin>()
                        .eq(status != null, GameSkin::getStatus, status)
                        .eq(characterId != null, GameSkin::getCharacterId, characterId)
                        .eq(rarity != null, GameSkin::getRarity, rarity)
                        .and(StringUtils.hasText(keyword),
                                wrapper -> wrapper.like(GameSkin::getName, keyword)
                                        .or().like(GameSkin::getSkinCode, keyword))
                        .orderByDesc(GameSkin::getSortOrder)
                        .orderByDesc(GameSkin::getId)
        );
    }

    @Override
    public Page<GameItem> pageItems(int page, int size, String keyword, Integer itemType, Integer status) {
        return gameItemMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<GameItem>()
                        .eq(status != null, GameItem::getStatus, status)
                        .eq(itemType != null, GameItem::getItemType, itemType)
                        .and(StringUtils.hasText(keyword),
                                wrapper -> wrapper.like(GameItem::getName, keyword)
                                        .or().like(GameItem::getItemCode, keyword))
                        .orderByDesc(GameItem::getSortOrder)
                        .orderByDesc(GameItem::getId)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createCharacter(GameCharacter character) {
        validateCharacter(character, true);
        touchCreateFields(character);
        gameCharacterMapper.insert(character);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCharacter(GameCharacter character) {
        GameCharacter existing = requireCharacter(character.getId());
        validateCharacter(character, false);
        character.setCreateTime(existing.getCreateTime());
        character.setUpdateTime(LocalDateTime.now());
        gameCharacterMapper.updateById(character);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableCharacter(Long id) {
        GameCharacter character = requireCharacter(id);
        if (character.getStatus() != null && character.getStatus() == DISABLED_STATUS) {
            return;
        }
        character.setStatus(DISABLED_STATUS);
        character.setUpdateTime(LocalDateTime.now());
        gameCharacterMapper.updateById(character);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createSkin(GameSkin skin) {
        validateSkin(skin, true);
        requireCharacter(skin.getCharacterId());
        touchCreateFields(skin);
        gameSkinMapper.insert(skin);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSkin(GameSkin skin) {
        GameSkin existing = requireSkin(skin.getId());
        validateSkin(skin, false);
        requireCharacter(skin.getCharacterId());
        skin.setCreateTime(existing.getCreateTime());
        skin.setUpdateTime(LocalDateTime.now());
        gameSkinMapper.updateById(skin);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableSkin(Long id) {
        GameSkin skin = requireSkin(id);
        if (skin.getStatus() != null && skin.getStatus() == DISABLED_STATUS) {
            return;
        }
        skin.setStatus(DISABLED_STATUS);
        skin.setUpdateTime(LocalDateTime.now());
        gameSkinMapper.updateById(skin);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createItem(GameItem item) {
        validateItem(item, true);
        touchCreateFields(item);
        gameItemMapper.insert(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateItem(GameItem item) {
        GameItem existing = requireItem(item.getId());
        validateItem(item, false);
        item.setCreateTime(existing.getCreateTime());
        item.setUpdateTime(LocalDateTime.now());
        gameItemMapper.updateById(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableItem(Long id) {
        GameItem item = requireItem(id);
        if (item.getStatus() != null && item.getStatus() == DISABLED_STATUS) {
            return;
        }
        item.setStatus(DISABLED_STATUS);
        item.setUpdateTime(LocalDateTime.now());
        gameItemMapper.updateById(item);
    }

    @Override
    public List<SignInReward> listSignInRewards() {
        return signInRewardMapper.selectList(new LambdaQueryWrapper<SignInReward>()
                .orderByAsc(SignInReward::getDayIndex));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createSignInReward(SignInReward reward) {
        validateSignInReward(reward, true);
        reward.setCreateTime(LocalDateTime.now());
        reward.setUpdateTime(LocalDateTime.now());
        if (reward.getStatus() == null) {
            reward.setStatus(ACTIVE_STATUS);
        }
        signInRewardMapper.insert(reward);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSignInReward(SignInReward reward) {
        SignInReward existing = signInRewardMapper.selectById(reward.getId());
        if (existing == null) {
            throw new BusinessException("签到奖励不存在");
        }
        validateSignInReward(reward, false);
        reward.setCreateTime(existing.getCreateTime());
        reward.setUpdateTime(LocalDateTime.now());
        signInRewardMapper.updateById(reward);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSignInReward(Long id) {
        if (signInRewardMapper.selectById(id) == null) {
            throw new BusinessException("签到奖励不存在");
        }
        signInRewardMapper.deleteById(id);
    }

    @Override
    public CharacterDetail getCharacterDetail(String characterCode) {
        return characterDetailRepository.findByCharacterCode(characterCode).orElse(null);
    }

    @Override
    public void saveCharacterDetail(CharacterDetail detail) {
        requireCharacterByCode(detail.getCharacterCode());
        upsertCharacterDetail(detail);
    }

    @Override
    public SkinDetail getSkinDetail(String skinCode) {
        return skinDetailRepository.findBySkinCode(skinCode).orElse(null);
    }

    @Override
    public void saveSkinDetail(SkinDetail detail) {
        requireSkinByCode(detail.getSkinCode());
        upsertSkinDetail(detail);
    }

    @Override
    public ItemDetail getItemDetail(String itemCode) {
        return itemDetailRepository.findByItemCode(itemCode).orElse(null);
    }

    @Override
    public void saveItemDetail(ItemDetail detail) {
        requireItemByCode(detail.getItemCode());
        upsertItemDetail(detail);
    }

    private void validateCharacter(GameCharacter character, boolean creating) {
        if (!StringUtils.hasText(character.getCharacterCode())) {
            throw new BusinessException("角色编码不能为空");
        }
        if (!StringUtils.hasText(character.getName())) {
            throw new BusinessException("角色名称不能为空");
        }
        Long duplicate = gameCharacterMapper.selectCount(new LambdaQueryWrapper<GameCharacter>()
                .eq(GameCharacter::getCharacterCode, character.getCharacterCode())
                .ne(!creating && character.getId() != null, GameCharacter::getId, character.getId()));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException("角色编码已存在");
        }
    }

    private void validateSkin(GameSkin skin, boolean creating) {
        if (!StringUtils.hasText(skin.getSkinCode())) {
            throw new BusinessException("皮肤编码不能为空");
        }
        if (!StringUtils.hasText(skin.getName())) {
            throw new BusinessException("皮肤名称不能为空");
        }
        Long duplicate = gameSkinMapper.selectCount(new LambdaQueryWrapper<GameSkin>()
                .eq(GameSkin::getSkinCode, skin.getSkinCode())
                .ne(!creating && skin.getId() != null, GameSkin::getId, skin.getId()));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException("皮肤编码已存在");
        }
    }

    private void validateItem(GameItem item, boolean creating) {
        if (!StringUtils.hasText(item.getItemCode())) {
            throw new BusinessException("物品编码不能为空");
        }
        if (!StringUtils.hasText(item.getName())) {
            throw new BusinessException("物品名称不能为空");
        }
        Long duplicate = gameItemMapper.selectCount(new LambdaQueryWrapper<GameItem>()
                .eq(GameItem::getItemCode, item.getItemCode())
                .ne(!creating && item.getId() != null, GameItem::getId, item.getId()));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException("物品编码已存在");
        }
    }

    private void validateSignInReward(SignInReward reward, boolean creating) {
        if (reward.getDayIndex() == null || reward.getDayIndex() <= 0) {
            throw new BusinessException("签到天数不能为空");
        }
        Long duplicate = signInRewardMapper.selectCount(new LambdaQueryWrapper<SignInReward>()
                .eq(SignInReward::getDayIndex, reward.getDayIndex())
                .ne(!creating && reward.getId() != null, SignInReward::getId, reward.getId()));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException("该天数的签到奖励已存在");
        }
    }

    private void touchCreateFields(GameCharacter character) {
        LocalDateTime now = LocalDateTime.now();
        character.setCreateTime(now);
        character.setUpdateTime(now);
        if (character.getStatus() == null) {
            character.setStatus(ACTIVE_STATUS);
        }
        if (character.getSortOrder() == null) {
            character.setSortOrder(0);
        }
        if (character.getVersion() == null) {
            character.setVersion(0);
        }
    }

    private void touchCreateFields(GameSkin skin) {
        LocalDateTime now = LocalDateTime.now();
        skin.setCreateTime(now);
        skin.setUpdateTime(now);
        if (skin.getStatus() == null) {
            skin.setStatus(ACTIVE_STATUS);
        }
        if (skin.getSortOrder() == null) {
            skin.setSortOrder(0);
        }
        if (skin.getVersion() == null) {
            skin.setVersion(0);
        }
    }

    private void touchCreateFields(GameItem item) {
        LocalDateTime now = LocalDateTime.now();
        item.setCreateTime(now);
        item.setUpdateTime(now);
        if (item.getStatus() == null) {
            item.setStatus(ACTIVE_STATUS);
        }
        if (item.getSortOrder() == null) {
            item.setSortOrder(0);
        }
        if (item.getVersion() == null) {
            item.setVersion(0);
        }
    }

    private GameCharacter requireCharacter(Long id) {
        if (id == null) {
            throw new BusinessException("角色ID不能为空");
        }
        GameCharacter character = gameCharacterMapper.selectById(id);
        if (character == null) {
            throw new BusinessException("角色不存在");
        }
        return character;
    }

    private GameCharacter requireCharacterByCode(String code) {
        GameCharacter character = gameCharacterMapper.selectOne(new LambdaQueryWrapper<GameCharacter>()
                .eq(GameCharacter::getCharacterCode, code)
                .last("limit 1"));
        if (character == null) {
            throw new BusinessException("角色不存在");
        }
        return character;
    }

    private GameSkin requireSkin(Long id) {
        if (id == null) {
            throw new BusinessException("皮肤ID不能为空");
        }
        GameSkin skin = gameSkinMapper.selectById(id);
        if (skin == null) {
            throw new BusinessException("皮肤不存在");
        }
        return skin;
    }

    private GameSkin requireSkinByCode(String code) {
        GameSkin skin = gameSkinMapper.selectOne(new LambdaQueryWrapper<GameSkin>()
                .eq(GameSkin::getSkinCode, code)
                .last("limit 1"));
        if (skin == null) {
            throw new BusinessException("皮肤不存在");
        }
        return skin;
    }

    private GameItem requireItem(Long id) {
        if (id == null) {
            throw new BusinessException("物品ID不能为空");
        }
        GameItem item = gameItemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException("物品不存在");
        }
        return item;
    }

    private GameItem requireItemByCode(String code) {
        GameItem item = gameItemMapper.selectOne(new LambdaQueryWrapper<GameItem>()
                .eq(GameItem::getItemCode, code)
                .last("limit 1"));
        if (item == null) {
            throw new BusinessException("物品不存在");
        }
        return item;
    }

    private void upsertCharacterDetail(CharacterDetail detail) {
        CharacterDetail existing = characterDetailRepository.findByCharacterCode(detail.getCharacterCode()).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            detail.setId(existing.getId());
            detail.setCreateTime(existing.getCreateTime());
        } else {
            detail.setCreateTime(now);
        }
        detail.setUpdateTime(now);
        characterDetailRepository.save(detail);
    }

    private void upsertSkinDetail(SkinDetail detail) {
        SkinDetail existing = skinDetailRepository.findBySkinCode(detail.getSkinCode()).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            detail.setId(existing.getId());
            detail.setCreateTime(existing.getCreateTime());
        } else {
            detail.setCreateTime(now);
        }
        detail.setUpdateTime(now);
        skinDetailRepository.save(detail);
    }

    private void upsertItemDetail(ItemDetail detail) {
        ItemDetail existing = itemDetailRepository.findByItemCode(detail.getItemCode()).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            detail.setId(existing.getId());
            detail.setCreateTime(existing.getCreateTime());
        } else {
            detail.setCreateTime(now);
        }
        detail.setUpdateTime(now);
        itemDetailRepository.save(detail);
    }
}
