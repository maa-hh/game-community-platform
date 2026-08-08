package com.game.community.user.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.constant.ApiErrorCodes;
import com.game.community.common.constant.cosmetic.CosmeticConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
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
import com.game.community.model.entity.user.User;
import com.game.community.model.vo.cosmetic.ActiveEffectVO;
import com.game.community.model.vo.cosmetic.CosmeticDefVO;
import com.game.community.model.vo.cosmetic.CosmeticEquippedVO;
import com.game.community.model.vo.cosmetic.CosmeticGrantResultVO;
import com.game.community.model.vo.cosmetic.CosmeticItemStateVO;
import com.game.community.model.vo.cosmetic.CosmeticPurchaseCheckVO;
import com.game.community.model.vo.cosmetic.UserCosmeticVO;
import com.game.community.model.vo.cosmetic.UserDecorationVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.user.mapper.CosmeticDefMapper;
import com.game.community.user.mapper.CosmeticGrantRecordMapper;
import com.game.community.user.mapper.UserActiveEffectMapper;
import com.game.community.user.mapper.UserCosmeticLoadoutMapper;
import com.game.community.user.mapper.UserCosmeticMapper;
import com.game.community.user.mapper.UserCosmeticUseLogMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.service.CosmeticService;
import com.game.community.user.service.UserQueryService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CosmeticServiceImpl implements CosmeticService {

    private final CosmeticDefMapper cosmeticDefMapper;
    private final UserCosmeticMapper userCosmeticMapper;
    private final UserCosmeticLoadoutMapper loadoutMapper;
    private final UserCosmeticUseLogMapper useLogMapper;
    private final UserActiveEffectMapper activeEffectMapper;
    private final CosmeticGrantRecordMapper grantRecordMapper;
    private final RedisUtils redisUtils;
    private final UserMapper userMapper;
    private final UserQueryService userQueryService;

    /** 执行 pageDefs 对应的业务处理。 */
    @Override
    public PageResult<CosmeticDefVO> pageDefs(Long page, Long size, String category, Integer status) {
        long current = page == null || page < CosmeticConstants.FIRST_PAGE
                ? CosmeticConstants.FIRST_PAGE : page;
        long pageSize = size == null || size < CosmeticConstants.DEFAULT_PAGE_SIZE
                ? CosmeticConstants.DEFAULT_PAGE_SIZE : Math.min(size, CosmeticConstants.MAX_PAGE_SIZE);
        Page<CosmeticDef> result = cosmeticDefMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<CosmeticDef>()
                        .eq(StringUtils.hasText(category), CosmeticDef::getCategory, category)
                        .eq(status != null, CosmeticDef::getStatus, status)
                        .orderByDesc(CosmeticDef::getId));
        return PageResult.of(result.getRecords().stream().map(this::toDefVO).toList(), current, pageSize, result.getTotal());
    }

    /** 执行 saveDef 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CosmeticDefVO saveDef(SaveCosmeticDefDTO dto) {
        if (CosmeticConstants.EffectMode.EQUIP.equals(dto.getEffectMode())
                && !StringUtils.hasText(dto.getSlot())) {
            throw new BusinessException("装备类装扮必须指定槽位");
        }
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
        entity.setSlot(StringUtils.hasText(dto.getSlot())
                ? dto.getSlot().trim().toUpperCase() : dto.getCategory());
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

    /** 执行 pageBackpack 对应的业务处理。 */
    @Override
    public PageResult<UserCosmeticVO> pageBackpack(Long userId, Long page, Long size,
                                                    String effectMode, String category,
                                                    Boolean equipped, String state, String keyword) {
        long current = page == null || page < CosmeticConstants.FIRST_PAGE
                ? CosmeticConstants.FIRST_PAGE : page;
        long pageSize = size == null || size < CosmeticConstants.DEFAULT_PAGE_SIZE
                ? CosmeticConstants.DEFAULT_PAGE_SIZE : Math.min(size, CosmeticConstants.MAX_PAGE_SIZE);
        String normalizedEffectMode = normalizeFilter(effectMode, Set.of(
                CosmeticConstants.EffectMode.EQUIP, CosmeticConstants.EffectMode.CONSUMABLE));
        String normalizedCategory = normalizeFilter(category, Set.of(
                CosmeticConstants.Category.AVATAR_FRAME, CosmeticConstants.Category.COMMENT_CARD,
                CosmeticConstants.Category.COMMENT_FONT, CosmeticConstants.Category.POST_CARD,
                CosmeticConstants.Category.PROFILE_BG));
        String normalizedState = normalizeFilter(state, Set.of(
                CosmeticConstants.State.ACTIVE, CosmeticConstants.State.EXPIRED));
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        Page<UserCosmetic> pageResult = new Page<>(current, pageSize);
        userCosmeticMapper.selectBackpackPage(pageResult, userId, normalizedEffectMode,
                normalizedCategory, equipped, normalizedState, normalizedKeyword);

        List<UserCosmetic> records = pageResult.getRecords();
        if (records == null || records.isEmpty()) {
            return PageResult.of(List.of(), current, pageSize, pageResult.getTotal());
        }
        Map<String, CosmeticDef> defs = loadDefs(records.stream()
                .map(UserCosmetic::getCosmeticCode)
                .toList());
        UserCosmeticLoadout loadout = loadoutMapper.selectById(userId);
        List<UserCosmeticVO> result = records.stream()
                .map(item -> toBackpackVO(item, defs.get(item.getCosmeticCode()), loadout))
                .filter(Objects::nonNull)
                .toList();
        return PageResult.of(result, current, pageSize, pageResult.getTotal());
    }

    /** 执行 normalizeFilter 对应的业务处理。 */
    private String normalizeFilter(String value, Set<String> allowedValues) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return allowedValues.contains(normalized) ? normalized : null;
    }

    /** 执行 getDecoration 对应的业务处理。 */
    @Override
    public UserDecorationVO getDecoration(Long userId) {
        User user = userMapper.selectById(userId);
        UserCosmeticLoadout loadout = loadoutMapper.selectById(userId);
        Map<String, CosmeticDef> defs = loadDefs(loadoutCodes(loadout));
        List<UserActiveEffect> effects = loadActiveEffects(List.of(userId), LocalDateTime.now());
        return buildDecoration(user == null ? null : user.getAccountId(), loadout, defs, effects);
    }

    /** 执行 grantCosmetic 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CosmeticGrantResultVO grantCosmetic(GrantCosmeticDTO dto) {
        CosmeticDef def = requireEnabledDef(dto.getCosmeticCode());
        int quantity = dto.getQuantity() == null || dto.getQuantity() < CosmeticConstants.DEFAULT_QUANTITY
                ? CosmeticConstants.DEFAULT_QUANTITY : dto.getQuantity();
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
        boolean stackable = CosmeticConstants.EffectMode.CONSUMABLE.equals(def.getEffectMode());
        userCosmeticMapper.upsertOwned(
                dto.getUserId(), def.getCode(), stackable ? quantity : CosmeticConstants.DEFAULT_QUANTITY,
                StringUtils.hasText(dto.getSourceType())
                        ? dto.getSourceType() : CosmeticConstants.SourceType.SHOP,
                dto.getOrderNo(), stackable ? CosmeticConstants.STACKABLE : CosmeticConstants.NON_STACKABLE);
        return buildGrantResult(def.getCode(), quantity, true);
    }

    /** 执行 checkOwnershipBlock 对应的业务处理。 */
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

    /** 执行 getItemState 对应的业务处理。 */
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

    /** 执行 batchDecorationsByAccountIds 对应的业务处理。 */
    @Override
    public Map<Long, UserDecorationVO> batchDecorationsByAccountIds(List<Long> accountIds) {
        List<Long> normalizedAccountIds = accountIds == null
                ? List.of()
                : accountIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedAccountIds.isEmpty()) {
            return Map.of();
        }

        Result<List<UserCardInternalVO>> usersResult =
                userQueryService.getUsersInternalByAccountIds(normalizedAccountIds);
        List<UserCardInternalVO> users = usersResult == null || usersResult.getData() == null
                ? List.of()
                : usersResult.getData();
        Map<Long, Long> accountIdsByUserId = users.stream()
                .filter(user -> user.getUserId() != null && user.getAccountId() != null)
                .collect(Collectors.toMap(UserCardInternalVO::getUserId,
                        UserCardInternalVO::getAccountId, (a, b) -> a));
        Map<Long, UserDecorationVO> decorations = batchDecorationsInternal(users.stream()
                .map(UserCardInternalVO::getUserId)
                .filter(Objects::nonNull)
                .toList(), accountIdsByUserId);

        Map<Long, UserDecorationVO> result = new LinkedHashMap<>();
        for (UserCardInternalVO user : users) {
            UserDecorationVO decoration = decorations.get(user.getUserId());
            if (user.getAccountId() != null && decoration != null) {
                result.put(user.getAccountId(), decoration);
            }
        }
        return result;
    }

    /** 执行 batchDecorationsInternal 对应的业务处理。 */
    private Map<Long, UserDecorationVO> batchDecorationsInternal(
            List<Long> userIds, Map<Long, Long> accountIdsByUserId) {
        List<Long> distinct = userIds == null
                ? List.of() : userIds.stream().filter(Objects::nonNull).distinct().toList();
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
            result.put(userId, buildDecoration(
                    accountIdsByUserId == null ? null : accountIdsByUserId.get(userId),
                    loadouts.get(userId), defs,
                    effectsByUser.getOrDefault(userId, List.of())));
        }
        return result;
    }

    /** 执行 equip 对应的业务处理。 */
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
            loadout.setVersion(CosmeticConstants.INITIAL_VERSION);
            loadout.setUpdateTime(LocalDateTime.now());
            applySlot(loadout, slot, def.getCode());
            if (loadoutMapper.insertIgnore(loadout) == 1) {
                return;
            }
            loadout = loadoutMapper.selectById(userId);
            if (loadout == null) {
                throw new BusinessException("装备状态初始化失败，请重试");
            }
        }
        applySlot(loadout, slot, def.getCode());
        loadout.setUpdateTime(LocalDateTime.now());
        if (loadoutMapper.updateById(loadout) == 0) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "装备状态已变化，请刷新后重试");
        }
    }

    /** 执行 unequip 对应的业务处理。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unequip(Long userId, UnequipCosmeticDTO dto) {
        String slot = dto.getSlot().trim().toUpperCase();
        UserCosmeticLoadout loadout = loadoutMapper.selectById(userId);
        if (loadout == null || !isSlotEquipped(loadout, slot)) {
            return;
        }
        applySlot(loadout, slot, null);
        loadout.setUpdateTime(LocalDateTime.now());
        if (loadoutMapper.updateById(loadout) == 0) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "装备状态已变化，请刷新后重试");
        }
    }

    /** 执行 useConsumable 对应的业务处理。 */
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

    /** 执行 requireOwned 对应的业务处理。 */
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

    /** 执行 requireEnabledDef 对应的业务处理。 */
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

    /** 执行 loadDefs 对应的业务处理。 */
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
                    .map(code -> CosmeticConstants.DEF_CACHE_KEY_PREFIX + code)
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

    /** 执行 findDef 对应的业务处理。 */
    private CosmeticDef findDef(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return loadDefs(List.of(code.trim())).get(code.trim());
    }

    /** 执行 cacheDef 对应的业务处理。 */
    private void cacheDef(CosmeticDef def) {
        if (def == null || !StringUtils.hasText(def.getCode())) {
            return;
        }
        try {
            redisUtils.setEx(CosmeticConstants.DEF_CACHE_KEY_PREFIX + def.getCode(),
                    JSON.toJSONString(def), CosmeticConstants.DEF_CACHE_SECONDS);
        } catch (RuntimeException e) {
            log.warn("装扮定义缓存写入失败，code={}", def.getCode(), e);
        }
    }

    /** 执行 evictDefCache 对应的业务处理。 */
    private void evictDefCache(String code) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        try {
            redisUtils.del(CosmeticConstants.DEF_CACHE_KEY_PREFIX + code);
        } catch (RuntimeException e) {
            log.warn("装扮定义缓存删除失败，code={}", code, e);
        }
    }

    /** 执行 toBackpackVO 对应的业务处理。 */
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
        boolean expired = owned.getExpireAt() != null
                && !owned.getExpireAt().isAfter(LocalDateTime.now());
        vo.setState(expired ? CosmeticConstants.State.EXPIRED : CosmeticConstants.State.ACTIVE);
        vo.setCanUse(CosmeticConstants.EffectMode.CONSUMABLE.equals(def.getEffectMode())
                && owned.getQuantity() != null && owned.getQuantity() > 0
                && !expired);
        return vo;
    }

    /** 执行 isEquipped 对应的业务处理。 */
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

    /** 执行 buildEquipped 对应的业务处理。 */
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

    /** 执行 loadActiveEffects 对应的业务处理。 */
    private List<UserActiveEffect> loadActiveEffects(List<Long> userIds, LocalDateTime now) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return activeEffectMapper.selectActiveByUserIds(userIds, now).stream()
                .sorted(Comparator.comparing(UserActiveEffect::getUserId)
                        .thenComparing(UserActiveEffect::getStartAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** 执行 buildDecoration 对应的业务处理。 */
    private UserDecorationVO buildDecoration(Long accountId,
                                             UserCosmeticLoadout loadout,
                                             Map<String, CosmeticDef> defs,
                                             List<UserActiveEffect> effects) {
        UserDecorationVO vo = new UserDecorationVO();
        vo.setAccountId(accountId);
        if (loadout != null) {
            vo.setAvatarFrame(buildEquipped(loadout.getAvatarFrameCode(), defs));
            vo.setCommentCard(buildEquipped(loadout.getCommentCardCode(), defs));
            vo.setCommentFont(buildEquipped(loadout.getCommentFontCode(), defs));
            vo.setPostCard(buildEquipped(loadout.getPostCardCode(), defs));
            vo.setProfileBg(buildEquipped(loadout.getProfileBgCode(), defs));
        }
        vo.setActiveEffects(effects.stream().map(effect -> {
            ActiveEffectVO effectVO = new ActiveEffectVO();
            BeanUtils.copyProperties(effect, effectVO);
            return effectVO;
        }).toList());
        return vo;
    }

    /** 执行 loadoutCodes 对应的业务处理。 */
    private List<String> loadoutCodes(UserCosmeticLoadout loadout) {
        if (loadout == null) {
            return List.of();
        }
        return java.util.stream.Stream.of(loadout.getAvatarFrameCode(), loadout.getCommentCardCode(),
                        loadout.getCommentFontCode(), loadout.getPostCardCode(), loadout.getProfileBgCode())
                .filter(StringUtils::hasText)
                .toList();
    }

    /** 执行 applySlot 对应的业务处理。 */
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

    /** 执行 isSlotEquipped 对应的业务处理。 */
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

    /** 执行 toDefVO 对应的业务处理。 */
    private CosmeticDefVO toDefVO(CosmeticDef def) {
        CosmeticDefVO vo = new CosmeticDefVO();
        BeanUtils.copyProperties(def, vo);
        return vo;
    }

    /** 执行 buildGrantResult 对应的业务处理。 */
    private CosmeticGrantResultVO buildGrantResult(String code, int quantity, boolean granted) {
        CosmeticGrantResultVO vo = new CosmeticGrantResultVO();
        vo.setCosmeticCode(code);
        vo.setQuantity(quantity);
        vo.setGranted(granted);
        return vo;
    }
}
