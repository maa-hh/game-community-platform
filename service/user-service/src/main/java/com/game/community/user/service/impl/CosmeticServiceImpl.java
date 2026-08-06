package com.game.community.user.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.constant.cosmetic.CosmeticConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.cosmetic.EquipCosmeticDTO;
import com.game.community.model.dto.cosmetic.GrantCosmeticDTO;
import com.game.community.model.dto.cosmetic.SaveCosmeticDefDTO;
import com.game.community.model.dto.cosmetic.UnequipCosmeticDTO;
import com.game.community.model.dto.cosmetic.UseConsumableCosmeticDTO;
import com.game.community.model.entity.cosmetic.CosmeticDef;
import com.game.community.model.entity.cosmetic.CosmeticGrantRecord;
import com.game.community.model.entity.cosmetic.UserActiveEffect;
import com.game.community.model.entity.cosmetic.UserCosmetic;
import com.game.community.model.entity.cosmetic.UserCosmeticLoadout;
import com.game.community.model.entity.cosmetic.UserCosmeticUseLog;
import com.game.community.model.vo.cosmetic.ActiveEffectVO;
import com.game.community.model.vo.cosmetic.CosmeticDefVO;
import com.game.community.model.vo.cosmetic.CosmeticEquippedVO;
import com.game.community.model.vo.cosmetic.CosmeticGrantResultVO;
import com.game.community.model.vo.cosmetic.CosmeticItemStateVO;
import com.game.community.model.vo.cosmetic.CosmeticPurchaseCheckVO;
import com.game.community.model.vo.cosmetic.UserCosmeticVO;
import com.game.community.model.vo.cosmetic.UserDecorationVO;
import com.game.community.user.mapper.CosmeticDefMapper;
import com.game.community.user.mapper.CosmeticGrantRecordMapper;
import com.game.community.user.mapper.UserActiveEffectMapper;
import com.game.community.user.mapper.UserCosmeticLoadoutMapper;
import com.game.community.user.mapper.UserCosmeticMapper;
import com.game.community.user.mapper.UserCosmeticUseLogMapper;
import com.game.community.user.service.CosmeticService;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CosmeticServiceImpl implements CosmeticService {

    private static final String COSMETIC_DEF_CACHE_PREFIX = "user:cosmetic:def:";
    private static final long COSMETIC_DEF_CACHE_SECONDS = 600;

    private final CosmeticDefMapper cosmeticDefMapper;
    private final UserCosmeticMapper userCosmeticMapper;
    private final UserCosmeticLoadoutMapper loadoutMapper;
    private final UserCosmeticUseLogMapper useLogMapper;
    private final UserActiveEffectMapper activeEffectMapper;
    private final CosmeticGrantRecordMapper grantRecordMapper;
    private final RedisUtils redisUtils;

    @Override
    public PageResult<CosmeticDefVO> pageDefs(Long page, Long size, String category, Integer status) {
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 20 : Math.min(size, 100);
        Page<CosmeticDef> result = cosmeticDefMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<CosmeticDef>()
                        .eq(StringUtils.hasText(category), CosmeticDef::getCategory, category)
                        .eq(status != null, CosmeticDef::getStatus, status)
                        .orderByDesc(CosmeticDef::getId));
        return PageResult.of(result.getRecords().stream().map(this::toDefVO).toList(), current, pageSize, result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CosmeticDefVO saveDef(SaveCosmeticDefDTO dto) {
        validateDef(dto);
        CosmeticDef entity;
        if (dto.getId() != null) {
            entity = cosmeticDefMapper.selectById(dto.getId());
            if (entity == null) {
                throw new BusinessException("装扮不存在");
            }
        } else {
            entity = findDef(dto.getCode());
            if (entity == null) {
                entity = new CosmeticDef();
                entity.setCreateTime(LocalDateTime.now());
            }
        }
        String oldCode = entity.getCode();
        entity.setCode(dto.getCode().trim());
        entity.setName(dto.getName().trim());
        entity.setCategory(dto.getCategory());
        entity.setEffectMode(dto.getEffectMode());
        entity.setSlot(resolveSlot(dto));
        entity.setPreviewUrl(dto.getPreviewUrl());
        entity.setAssetJson(dto.getAssetJson());
        entity.setConsumableConfig(dto.getConsumableConfig());
        entity.setDefaultDurationSeconds(dto.getDefaultDurationSeconds());
        entity.setStatus(dto.getStatus() == null ? CosmeticConstants.STATUS_ON : dto.getStatus());
        entity.setUpdateTime(LocalDateTime.now());
        if (entity.getId() == null) {
            cosmeticDefMapper.insert(entity);
        } else {
            cosmeticDefMapper.updateById(entity);
        }
        evictDefCache(oldCode);
        cacheDef(entity);
        return toDefVO(entity);
    }

    @Override
    public List<UserCosmeticVO> listBackpack(Long userId) {
        List<UserCosmetic> owned = userCosmeticMapper.selectList(new LambdaQueryWrapper<UserCosmetic>()
                .eq(UserCosmetic::getUserId, userId)
                .gt(UserCosmetic::getQuantity, 0)
                .orderByDesc(UserCosmetic::getAcquiredAt));
        if (owned.isEmpty()) {
            return List.of();
        }
        Map<String, CosmeticDef> defs = loadDefs(owned.stream().map(UserCosmetic::getCosmeticCode).toList());
        UserCosmeticLoadout loadout = loadoutMapper.selectById(userId);
        return owned.stream()
                .map(item -> toBackpackVO(item, defs.get(item.getCosmeticCode()), loadout))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public UserDecorationVO getDecoration(Long userId) {
        UserCosmeticLoadout loadout = loadoutMapper.selectById(userId);
        Map<String, CosmeticDef> defs = loadDefs(loadoutCodes(loadout));
        List<UserActiveEffect> effects = loadActiveEffects(List.of(userId), LocalDateTime.now());
        return buildDecoration(userId, loadout, defs, effects);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CosmeticGrantResultVO grantCosmetic(GrantCosmeticDTO dto) {
        CosmeticDef def = requireEnabledDef(dto.getCosmeticCode());
        int quantity = dto.getQuantity() == null || dto.getQuantity() < 1 ? 1 : dto.getQuantity();
        CosmeticGrantRecord existing = grantRecordMapper.selectByOrderNo(dto.getOrderNo());
        if (existing != null) {
            return buildGrantResult(def.getCode(), existing.getQuantity(), true);
        }
        CosmeticGrantRecord record = new CosmeticGrantRecord();
        record.setOrderNo(dto.getOrderNo());
        record.setUserId(dto.getUserId());
        record.setCosmeticCode(def.getCode());
        record.setQuantity(quantity);
        record.setCreateTime(LocalDateTime.now());
        try {
            grantRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            CosmeticGrantRecord concurrent = grantRecordMapper.selectByOrderNo(dto.getOrderNo());
            return buildGrantResult(def.getCode(), concurrent == null ? quantity : concurrent.getQuantity(), true);
        }
        upsertOwned(dto.getUserId(), def, quantity, dto.getSourceType(), dto.getOrderNo());
        return buildGrantResult(def.getCode(), quantity, true);
    }

    @Override
    public CosmeticPurchaseCheckVO checkOwnershipBlock(Long userId, String cosmeticCode) {
        CosmeticPurchaseCheckVO vo = new CosmeticPurchaseCheckVO();
        vo.setCanBuy(true);
        CosmeticDef def = findDef(cosmeticCode);
        if (def == null) {
            vo.setCanBuy(false);
            vo.setReason("装扮不存在");
            return vo;
        }
        if (!CosmeticConstants.EffectMode.EQUIP.equals(def.getEffectMode())) {
            return vo;
        }
        UserCosmetic owned = userCosmeticMapper.selectByUserAndCode(userId, cosmeticCode);
        if (owned != null && owned.getQuantity() != null && owned.getQuantity() > 0
                && (owned.getExpireAt() == null || owned.getExpireAt().isAfter(LocalDateTime.now()))) {
            vo.setCanBuy(false);
            vo.setReason("已拥有该装扮");
        }
        return vo;
    }

    @Override
    public CosmeticItemStateVO getItemState(Long userId, String cosmeticCode) {
        CosmeticItemStateVO vo = new CosmeticItemStateVO();
        vo.setOwned(false);
        vo.setEquipped(false);
        if (userId == null || !StringUtils.hasText(cosmeticCode)) {
            return vo;
        }
        UserCosmetic owned = userCosmeticMapper.selectByUserAndCode(userId, cosmeticCode);
        boolean hasOwned = owned != null && owned.getQuantity() != null && owned.getQuantity() > 0;
        vo.setOwned(hasOwned);
        if (!hasOwned) {
            return vo;
        }
        CosmeticDef def = findDef(cosmeticCode);
        UserCosmeticLoadout loadout = loadoutMapper.selectById(userId);
        vo.setEquipped(def != null && isEquipped(loadout, def));
        return vo;
    }

    @Override
    public Map<Long, UserDecorationVO> batchDecorations(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserCosmeticLoadout> loadouts = loadoutMapper.selectByUserIds(distinct).stream()
                .collect(Collectors.toMap(UserCosmeticLoadout::getUserId, Function.identity(), (a, b) -> a));
        Map<String, CosmeticDef> defs = loadDefs(loadouts.values().stream()
                .flatMap(loadout -> loadoutCodes(loadout).stream())
                .distinct()
                .toList());
        Map<Long, List<UserActiveEffect>> effectsByUser = loadActiveEffects(distinct, LocalDateTime.now()).stream()
                .collect(Collectors.groupingBy(UserActiveEffect::getUserId));
        Map<Long, UserDecorationVO> result = new HashMap<>();
        for (Long userId : distinct) {
            result.put(userId, buildDecoration(userId, loadouts.get(userId), defs,
                    effectsByUser.getOrDefault(userId, List.of())));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void equip(Long userId, EquipCosmeticDTO dto) {
        CosmeticDef def = requireEnabledDef(dto.getCode());
        if (!CosmeticConstants.EffectMode.EQUIP.equals(def.getEffectMode())) {
            throw new BusinessException("该装扮不支持装备");
        }
        String slot = dto.getSlot().trim().toUpperCase();
        if (!Objects.equals(slot, def.getSlot())) {
            throw new BusinessException("装扮槽位不匹配");
        }
        requireOwned(userId, def.getCode());
        UserCosmeticLoadout loadout = loadoutMapper.selectById(userId);
        if (loadout == null) {
            loadout = new UserCosmeticLoadout();
            loadout.setUserId(userId);
            loadout.setUpdateTime(LocalDateTime.now());
            applySlot(loadout, slot, def.getCode());
            loadoutMapper.insert(loadout);
            return;
        }
        applySlot(loadout, slot, def.getCode());
        loadout.setUpdateTime(LocalDateTime.now());
        loadoutMapper.updateById(loadout);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unequip(Long userId, UnequipCosmeticDTO dto) {
        String slot = dto.getSlot().trim().toUpperCase();
        UserCosmeticLoadout loadout = loadoutMapper.selectById(userId);
        if (loadout == null || !isSlotEquipped(loadout, slot)) {
            return;
        }
        LambdaUpdateWrapper<UserCosmeticLoadout> wrapper = new LambdaUpdateWrapper<UserCosmeticLoadout>()
                .eq(UserCosmeticLoadout::getUserId, userId)
                .set(UserCosmeticLoadout::getUpdateTime, LocalDateTime.now());
        applySlotUpdate(wrapper, slot, null);
        loadoutMapper.update(null, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void useConsumable(Long userId, UseConsumableCosmeticDTO dto) {
        CosmeticDef def = requireEnabledDef(dto.getCode());
        if (!CosmeticConstants.EffectMode.CONSUMABLE.equals(def.getEffectMode())) {
            throw new BusinessException("该装扮不支持使用");
        }
        UserCosmetic owned = requireOwned(userId, def.getCode());
        if (owned.getQuantity() == null || owned.getQuantity() <= 0) {
            throw new BusinessException("装扮数量不足");
        }
        int updated = userCosmeticMapper.increaseQuantity(userId, def.getCode(), -1);
        if (updated == 0) {
            throw new BusinessException("装扮数量不足");
        }
        UserCosmeticUseLog log = new UserCosmeticUseLog();
        log.setUserId(userId);
        log.setCosmeticCode(def.getCode());
        log.setUseTime(LocalDateTime.now());
        useLogMapper.insert(log);
        if (def.getDefaultDurationSeconds() != null && def.getDefaultDurationSeconds() > 0) {
            UserActiveEffect effect = new UserActiveEffect();
            effect.setUserId(userId);
            effect.setEffectCode(def.getCode());
            effect.setSourceCosmeticCode(def.getCode());
            effect.setStartAt(LocalDateTime.now());
            effect.setExpireAt(LocalDateTime.now().plusSeconds(def.getDefaultDurationSeconds()));
            effect.setPayloadJson(def.getConsumableConfig());
            activeEffectMapper.insert(effect);
        }
    }

    private void upsertOwned(Long userId, CosmeticDef def, int quantity, String sourceType, String sourceRef) {
        UserCosmetic existing = userCosmeticMapper.selectByUserAndCode(userId, def.getCode());
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            UserCosmetic created = new UserCosmetic();
            created.setUserId(userId);
            created.setCosmeticCode(def.getCode());
            created.setQuantity(CosmeticConstants.EffectMode.EQUIP.equals(def.getEffectMode()) ? 1 : quantity);
            created.setSourceType(StringUtils.hasText(sourceType) ? sourceType : CosmeticConstants.SourceType.SHOP);
            created.setSourceRef(sourceRef);
            created.setAcquiredAt(now);
            created.setCreateTime(now);
            created.setUpdateTime(now);
            userCosmeticMapper.insert(created);
            return;
        }
        if (CosmeticConstants.EffectMode.CONSUMABLE.equals(def.getEffectMode())) {
            userCosmeticMapper.increaseQuantity(userId, def.getCode(), quantity);
            existing.setUpdateTime(now);
            userCosmeticMapper.updateById(existing);
        }
    }

    private UserCosmetic requireOwned(Long userId, String code) {
        UserCosmetic owned = userCosmeticMapper.selectByUserAndCode(userId, code);
        if (owned == null || owned.getQuantity() == null || owned.getQuantity() <= 0) {
            throw new BusinessException("尚未拥有该装扮");
        }
        if (owned.getExpireAt() != null && !owned.getExpireAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("装扮已过期");
        }
        return owned;
    }

    private CosmeticDef requireEnabledDef(String code) {
        CosmeticDef def = findDef(code);
        if (def == null) {
            throw new BusinessException("装扮不存在");
        }
        if (def.getStatus() == null || def.getStatus() != CosmeticConstants.STATUS_ON) {
            throw new BusinessException("装扮已下架");
        }
        return def;
    }

    private void validateDef(SaveCosmeticDefDTO dto) {
        if (CosmeticConstants.EffectMode.EQUIP.equals(dto.getEffectMode()) && !StringUtils.hasText(dto.getSlot())) {
            throw new BusinessException("装备类装扮必须指定槽位");
        }
    }

    private String resolveSlot(SaveCosmeticDefDTO dto) {
        if (StringUtils.hasText(dto.getSlot())) {
            return dto.getSlot().trim().toUpperCase();
        }
        return dto.getCategory();
    }

    private Map<String, CosmeticDef> loadDefs(List<String> codes) {
        List<String> distinctCodes = codes == null ? List.of() : codes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (distinctCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, CosmeticDef> result = new HashMap<>();
        List<String> missingCodes = new ArrayList<>();
        try {
            List<String> cacheValues = redisUtils.multiGet(distinctCodes.stream()
                    .map(this::defCacheKey)
                    .toArray(String[]::new));
            for (int i = 0; i < distinctCodes.size(); i++) {
                String value = i < cacheValues.size() ? cacheValues.get(i) : null;
                if (!StringUtils.hasText(value)) {
                    missingCodes.add(distinctCodes.get(i));
                    continue;
                }
                try {
                    CosmeticDef def = JSON.parseObject(value, CosmeticDef.class);
                    if (def == null) {
                        missingCodes.add(distinctCodes.get(i));
                    } else {
                        result.put(def.getCode(), def);
                    }
                } catch (RuntimeException e) {
                    log.warn("装扮定义缓存解析失败，回源查询 code={}", distinctCodes.get(i), e);
                    missingCodes.add(distinctCodes.get(i));
                }
            }
        } catch (RuntimeException e) {
            log.warn("装扮定义缓存读取失败，回源数据库", e);
            missingCodes.addAll(distinctCodes);
        }
        if (!missingCodes.isEmpty()) {
            List<CosmeticDef> dbDefs = cosmeticDefMapper.selectByCodes(missingCodes);
            for (CosmeticDef def : dbDefs) {
                result.put(def.getCode(), def);
                cacheDef(def);
            }
        }
        return result;
    }

    private CosmeticDef findDef(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return loadDefs(List.of(code.trim())).get(code.trim());
    }

    private void cacheDef(CosmeticDef def) {
        if (def == null || !StringUtils.hasText(def.getCode())) {
            return;
        }
        try {
            redisUtils.setEx(defCacheKey(def.getCode()), JSON.toJSONString(def), COSMETIC_DEF_CACHE_SECONDS);
        } catch (RuntimeException e) {
            log.warn("装扮定义缓存写入失败，code={}", def.getCode(), e);
        }
    }

    private void evictDefCache(String code) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        try {
            redisUtils.del(defCacheKey(code));
        } catch (RuntimeException e) {
            log.warn("装扮定义缓存删除失败，code={}", code, e);
        }
    }

    private String defCacheKey(String code) {
        return COSMETIC_DEF_CACHE_PREFIX + code;
    }

    private UserCosmeticVO toBackpackVO(UserCosmetic owned, CosmeticDef def, UserCosmeticLoadout loadout) {
        if (def == null) {
            return null;
        }
        UserCosmeticVO vo = new UserCosmeticVO();
        vo.setCosmeticCode(def.getCode());
        vo.setName(def.getName());
        vo.setCategory(def.getCategory());
        vo.setEffectMode(def.getEffectMode());
        vo.setSlot(def.getSlot());
        vo.setPreviewUrl(def.getPreviewUrl());
        vo.setAssetJson(def.getAssetJson());
        vo.setQuantity(owned.getQuantity());
        vo.setAcquiredAt(owned.getAcquiredAt());
        vo.setExpireAt(owned.getExpireAt());
        vo.setEquipped(isEquipped(loadout, def));
        vo.setCanUse(CosmeticConstants.EffectMode.CONSUMABLE.equals(def.getEffectMode())
                && owned.getQuantity() != null && owned.getQuantity() > 0);
        return vo;
    }

    private boolean isEquipped(UserCosmeticLoadout loadout, CosmeticDef def) {
        if (loadout == null || !StringUtils.hasText(def.getSlot())) {
            return false;
        }
        String code = def.getCode();
        return switch (def.getSlot()) {
            case CosmeticConstants.Slot.AVATAR_FRAME -> Objects.equals(loadout.getAvatarFrameCode(), code);
            case CosmeticConstants.Slot.COMMENT_CARD -> Objects.equals(loadout.getCommentCardCode(), code);
            case CosmeticConstants.Slot.COMMENT_FONT -> Objects.equals(loadout.getCommentFontCode(), code);
            case CosmeticConstants.Slot.POST_CARD -> Objects.equals(loadout.getPostCardCode(), code);
            case CosmeticConstants.Slot.PROFILE_BG -> Objects.equals(loadout.getProfileBgCode(), code);
            default -> false;
        };
    }

    private CosmeticEquippedVO buildEquipped(String code, Map<String, CosmeticDef> defs) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        CosmeticDef def = defs.get(code);
        if (def == null) {
            return null;
        }
        CosmeticEquippedVO vo = new CosmeticEquippedVO();
        vo.setCode(def.getCode());
        vo.setName(def.getName());
        vo.setCategory(def.getCategory());
        vo.setAssetJson(def.getAssetJson());
        return vo;
    }

    private List<UserActiveEffect> loadActiveEffects(List<Long> userIds, LocalDateTime now) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return activeEffectMapper.selectActiveByUserIds(userIds, now).stream()
                .sorted(Comparator.comparing(UserActiveEffect::getUserId)
                        .thenComparing(UserActiveEffect::getStartAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private UserDecorationVO buildDecoration(Long userId, UserCosmeticLoadout loadout,
                                             Map<String, CosmeticDef> defs,
                                             List<UserActiveEffect> effects) {
        UserDecorationVO vo = new UserDecorationVO();
        vo.setUserId(userId);
        if (loadout != null) {
            vo.setAvatarFrame(buildEquipped(loadout.getAvatarFrameCode(), defs));
            vo.setCommentCard(buildEquipped(loadout.getCommentCardCode(), defs));
            vo.setCommentFont(buildEquipped(loadout.getCommentFontCode(), defs));
            vo.setPostCard(buildEquipped(loadout.getPostCardCode(), defs));
            vo.setProfileBg(buildEquipped(loadout.getProfileBgCode(), defs));
        }
        vo.setActiveEffects(toActiveEffectVOs(effects));
        return vo;
    }

    private List<String> loadoutCodes(UserCosmeticLoadout loadout) {
        if (loadout == null) {
            return List.of();
        }
        return java.util.stream.Stream.of(loadout.getAvatarFrameCode(), loadout.getCommentCardCode(),
                        loadout.getCommentFontCode(), loadout.getPostCardCode(), loadout.getProfileBgCode())
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<ActiveEffectVO> toActiveEffectVOs(List<UserActiveEffect> effects) {
        List<ActiveEffectVO> result = new ArrayList<>();
        for (UserActiveEffect effect : effects) {
            ActiveEffectVO vo = new ActiveEffectVO();
            vo.setEffectCode(effect.getEffectCode());
            vo.setSourceCosmeticCode(effect.getSourceCosmeticCode());
            vo.setExpireAt(effect.getExpireAt());
            vo.setPayloadJson(effect.getPayloadJson());
            result.add(vo);
        }
        return result;
    }

    private void applySlot(UserCosmeticLoadout loadout, String slot, String code) {
        switch (slot) {
            case CosmeticConstants.Slot.AVATAR_FRAME -> loadout.setAvatarFrameCode(code);
            case CosmeticConstants.Slot.COMMENT_CARD -> loadout.setCommentCardCode(code);
            case CosmeticConstants.Slot.COMMENT_FONT -> loadout.setCommentFontCode(code);
            case CosmeticConstants.Slot.POST_CARD -> loadout.setPostCardCode(code);
            case CosmeticConstants.Slot.PROFILE_BG -> loadout.setProfileBgCode(code);
            default -> throw new BusinessException("未知槽位");
        }
    }

    private void applySlotUpdate(LambdaUpdateWrapper<UserCosmeticLoadout> wrapper, String slot, String code) {
        switch (slot) {
            case CosmeticConstants.Slot.AVATAR_FRAME -> wrapper.set(UserCosmeticLoadout::getAvatarFrameCode, code);
            case CosmeticConstants.Slot.COMMENT_CARD -> wrapper.set(UserCosmeticLoadout::getCommentCardCode, code);
            case CosmeticConstants.Slot.COMMENT_FONT -> wrapper.set(UserCosmeticLoadout::getCommentFontCode, code);
            case CosmeticConstants.Slot.POST_CARD -> wrapper.set(UserCosmeticLoadout::getPostCardCode, code);
            case CosmeticConstants.Slot.PROFILE_BG -> wrapper.set(UserCosmeticLoadout::getProfileBgCode, code);
            default -> throw new BusinessException("未知槽位");
        }
    }

    private boolean isSlotEquipped(UserCosmeticLoadout loadout, String slot) {
        return switch (slot) {
            case CosmeticConstants.Slot.AVATAR_FRAME -> StringUtils.hasText(loadout.getAvatarFrameCode());
            case CosmeticConstants.Slot.COMMENT_CARD -> StringUtils.hasText(loadout.getCommentCardCode());
            case CosmeticConstants.Slot.COMMENT_FONT -> StringUtils.hasText(loadout.getCommentFontCode());
            case CosmeticConstants.Slot.POST_CARD -> StringUtils.hasText(loadout.getPostCardCode());
            case CosmeticConstants.Slot.PROFILE_BG -> StringUtils.hasText(loadout.getProfileBgCode());
            default -> false;
        };
    }

    private CosmeticDefVO toDefVO(CosmeticDef def) {
        CosmeticDefVO vo = new CosmeticDefVO();
        BeanUtils.copyProperties(def, vo);
        return vo;
    }

    private CosmeticGrantResultVO buildGrantResult(String code, int quantity, boolean granted) {
        CosmeticGrantResultVO vo = new CosmeticGrantResultVO();
        vo.setCosmeticCode(code);
        vo.setQuantity(quantity);
        vo.setGranted(granted);
        return vo;
    }
}
