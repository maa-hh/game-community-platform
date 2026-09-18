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

    /** 分页查询装扮定义并转换为后台展示 VO。 */
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

    /** 新增或更新装扮定义，并原子刷新 Redis 定义缓存。 */
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

    /** 规范化背包筛选条件，批量加载定义后分页返回用户库存。 */
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

    /** 将枚举型筛选值规范化为允许值，非法值按未筛选处理。 */
    private String normalizeFilter(String value, Set<String> allowedValues) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return allowedValues.contains(normalized) ? normalized : null;
    }

    /** 按内部 userId 批量读取槽位和生效效果，构造装扮展示。 */
    @Override
    public UserDecorationVO getDecoration(Long userId) {
        if (userId == null) {
            throw new BusinessException("用户不存在");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return getDecorationInternal(userId, user.getAccountId());
    }

    /** 按已解析的用户标识读取装扮数据，避免 accountId 入口重复查询用户表。 */
    private UserDecorationVO getDecorationInternal(Long userId, Long accountId) {
        UserCosmeticLoadout loadout = loadoutMapper.selectById(userId);
        Map<String, CosmeticDef> defs = loadDefs(loadoutCodes(loadout));
        List<UserActiveEffect> effects = loadActiveEffects(List.of(userId), LocalDateTime.now());
        return buildDecoration(accountId, loadout, defs, effects);
    }

    /** 将对外 accountId 解析为内部 userId 后查询装扮展示。 */
    @Override
    public UserDecorationVO getDecorationByAccountId(Long accountId) {
        Result<UserCardInternalVO> result = userQueryService.getUserInternalByAccountId(accountId);
        if (result == null || result.getData() == null) {
            throw new BusinessException("用户不存在");
        }
        return getDecorationInternal(result.getData().getUserId(), result.getData().getAccountId());
    }

    /** 按订单号幂等写入发放记录并增加用户装扮库存。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CosmeticGrantResultVO grantCosmetic(GrantCosmeticDTO dto) {
        String cosmeticCode = normalizeRequired(dto.getCosmeticCode(), "装扮编码不能为空");
        String orderNo = normalizeRequired(dto.getOrderNo(), "订单号不能为空");
        int quantity = dto.getQuantity() == null ? CosmeticConstants.DEFAULT_QUANTITY : dto.getQuantity();
        if (quantity < CosmeticConstants.DEFAULT_QUANTITY) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, "数量至少为1");
        }
        CosmeticGrantRecord existing = grantRecordMapper.selectByOrderNo(orderNo);
        if (existing != null) {
            assertGrantReplay(existing, dto.getUserId(), cosmeticCode, quantity);
            return buildGrantResult(existing.getCosmeticCode(), existing.getQuantity(), true);
        }
        CosmeticDef def = requireEnabledDef(cosmeticCode);
        CosmeticGrantRecord record = new CosmeticGrantRecord();
        record.setOrderNo(orderNo);
        record.setUserId(dto.getUserId());
        record.setCosmeticCode(def.getCode());
        record.setQuantity(quantity);
        record.setCreateTime(LocalDateTime.now());
        try {
            grantRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            CosmeticGrantRecord concurrent = grantRecordMapper.selectByOrderNo(orderNo);
            if (concurrent == null) {
                throw new BusinessException(ApiErrorCodes.CONFLICT, "发放请求冲突，请重试");
            }
            assertGrantReplay(concurrent, dto.getUserId(), def.getCode(), quantity);
            return buildGrantResult(concurrent.getCosmeticCode(), concurrent.getQuantity(), true);
        }
        boolean stackable = CosmeticConstants.EffectMode.CONSUMABLE.equals(def.getEffectMode());
        int ownedUpdated = userCosmeticMapper.upsertOwned(
                dto.getUserId(), def.getCode(), stackable ? quantity : CosmeticConstants.DEFAULT_QUANTITY,
                StringUtils.hasText(dto.getSourceType())
                        ? dto.getSourceType() : CosmeticConstants.SourceType.SHOP,
                orderNo, stackable ? CosmeticConstants.STACKABLE : CosmeticConstants.NON_STACKABLE);
        if (ownedUpdated != 1) {
            throw new BusinessException(ApiErrorCodes.INTERNAL_ERROR, "装扮库存写入失败");
        }
        return buildGrantResult(def.getCode(), quantity, true);
    }

    /** 规范化幂等接口的必填字符串，避免同一业务请求产生多个幂等键。 */
    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ApiErrorCodes.BAD_REQUEST, message);
        }
        return value.trim();
    }

    /** 校验重复订单的业务参数，防止复用订单号给其他用户或装扮发放。 */
    private void assertGrantReplay(CosmeticGrantRecord record, Long userId,
                                   String cosmeticCode, int quantity) {
        if (!Objects.equals(record.getUserId(), userId)
                || !Objects.equals(record.getCosmeticCode(), cosmeticCode)
                || !Objects.equals(record.getQuantity(), quantity)) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "订单号已被其他发放请求使用");
        }
    }

    /** 判断装备类装扮是否已拥有并阻止重复购买。 */
    @Override
    public CosmeticPurchaseCheckVO checkOwnershipBlock(Long userId, String cosmeticCode) {
        CosmeticPurchaseCheckVO vo = new CosmeticPurchaseCheckVO();
        vo.setCanBuy(true);
        String normalizedCode = StringUtils.hasText(cosmeticCode) ? cosmeticCode.trim() : null;
        CosmeticDef def = findDef(normalizedCode);
        if (def == null) {
            vo.setCanBuy(false);
            vo.setReason("装扮不存在");
            return vo;
        }
        if (!CosmeticConstants.EffectMode.EQUIP.equals(def.getEffectMode())) {
            return vo;
        }
        UserCosmetic owned = userCosmeticMapper.selectByUserAndCode(userId, normalizedCode);
        if (owned != null && owned.getQuantity() != null && owned.getQuantity() > 0
                && (owned.getExpireAt() == null || owned.getExpireAt().isAfter(LocalDateTime.now()))) {
            vo.setCanBuy(false);
            vo.setReason("已拥有该装扮");
        }
        return vo;
    }

    /** 查询用户对指定装扮的拥有、过期和装备状态。 */
    @Override
    public CosmeticItemStateVO getItemState(Long userId, String cosmeticCode) {
        CosmeticItemStateVO vo = new CosmeticItemStateVO();
        vo.setOwned(false);
        vo.setEquipped(false);
        if (userId == null || !StringUtils.hasText(cosmeticCode)) {
            return vo;
        }
        String normalizedCode = cosmeticCode.trim();
        UserCosmetic owned = userCosmeticMapper.selectByUserAndCode(userId, normalizedCode);
        boolean hasOwned = owned != null && owned.getQuantity() != null && owned.getQuantity() > 0;
        vo.setOwned(hasOwned);
        if (!hasOwned) {
            return vo;
        }
        CosmeticDef def = findDef(normalizedCode);
        UserCosmeticLoadout loadout = loadoutMapper.selectById(userId);
        vo.setEquipped(def != null && isEquipped(loadout, def));
        return vo;
    }

    /** 批量读取库存、定义和装备槽位，保证商城列表只产生一次用户服务调用。 */
    @Override
    public Map<String, CosmeticItemStateVO> getItemStates(Long userId, List<String> cosmeticCodes) {
        if (userId == null || cosmeticCodes == null || cosmeticCodes.isEmpty()) {
            return Map.of();
        }
        List<String> codes = cosmeticCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<String, UserCosmetic> ownedByCode = userCosmeticMapper.selectByUserAndCodes(userId, codes).stream()
                .collect(Collectors.toMap(UserCosmetic::getCosmeticCode, Function.identity(), (a, b) -> a));
        Map<String, CosmeticDef> defs = loadDefs(codes);
        UserCosmeticLoadout loadout = loadoutMapper.selectById(userId);
        Map<String, CosmeticItemStateVO> result = new LinkedHashMap<>();
        for (String code : codes) {
            UserCosmetic owned = ownedByCode.get(code);
            boolean hasOwned = owned != null && owned.getQuantity() != null && owned.getQuantity() > 0;
            CosmeticItemStateVO state = new CosmeticItemStateVO();
            state.setOwned(hasOwned);
            CosmeticDef def = defs.get(code);
            state.setEquipped(hasOwned && def != null && isEquipped(loadout, def));
            result.put(code, state);
        }
        return result;
    }

    /** 批量完成 accountId 映射，并一次查询所有用户装扮数据。 */
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

    /** 批量加载槽位、定义和主动效果，组装按 userId 索引的结果。 */
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

    /** 校验装扮归属和槽位后，使用乐观锁更新装备槽位。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void equip(Long userId, EquipCosmeticDTO dto) {
        CosmeticDef def = requireEnabledDef(dto.getCode());
        if (!CosmeticConstants.EffectMode.EQUIP.equals(def.getEffectMode())) {
            throw new BusinessException("该装扮不支持装备");
        }
        String slot = dto.getSlot().trim().toUpperCase(Locale.ROOT);
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

    /** 使用槽位版本 CAS 卸下当前装备，避免并发覆盖。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unequip(Long userId, UnequipCosmeticDTO dto) {
        String slot = dto.getSlot().trim().toUpperCase(Locale.ROOT);
        UserCosmeticLoadout loadout = loadoutMapper.selectById(userId);
        if (loadout == null || !isSlotEquipped(loadout, slot)) {
            return;
        }
        if (loadoutMapper.clearSlot(userId, slot, loadout.getVersion()) == 0) {
            throw new BusinessException(ApiErrorCodes.CONFLICT, "装备状态已变化，请刷新后重试");
        }
    }

    /** 原子扣减消耗品库存，并记录使用日志和生效效果。 */
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

    /** 查询并校验用户拥有且未过期的装扮库存。 */
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

    /** 查询并校验装扮定义存在且处于上架状态。 */
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

    /** 优先批量读取 Redis 定义缓存，缺失项一次回源数据库。 */
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

    /** 查询单个规范化装扮定义。 */
    private CosmeticDef findDef(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return loadDefs(List.of(code.trim())).get(code.trim());
    }

    /** 写入单个装扮定义的共享 Redis 缓存，失败不阻断主流程。 */
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

    /** 删除装扮定义的共享缓存，避免更新后读取旧值。 */
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

    /** 将库存实体、定义和槽位状态转换为背包 VO。 */
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

    /** 根据定义槽位判断装扮是否位于当前 loadout。 */
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

    /** 根据装备编码构造单个装备展示 VO。 */
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

    /** 批量查询未过期主动效果并按用户、开始时间稳定排序。 */
    private List<UserActiveEffect> loadActiveEffects(List<Long> userIds, LocalDateTime now) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return activeEffectMapper.selectActiveByUserIds(userIds, now).stream()
                .sorted(Comparator.comparing(UserActiveEffect::getUserId)
                        .thenComparing(UserActiveEffect::getStartAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** 组装用户槽位装备和主动效果展示 VO。 */
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

    /** 提取 loadout 中所有非空装备编码。 */
    private List<String> loadoutCodes(UserCosmeticLoadout loadout) {
        if (loadout == null) {
            return List.of();
        }
        return java.util.stream.Stream.of(loadout.getAvatarFrameCode(), loadout.getCommentCardCode(),
                        loadout.getCommentFontCode(), loadout.getPostCardCode(), loadout.getProfileBgCode())
                .filter(StringUtils::hasText)
                .toList();
    }

    /** 将装扮编码写入指定槽位。 */
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

    /** 判断指定槽位当前是否已装备内容。 */
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

    /** 将装扮定义实体转换为公开定义 VO。 */
    private CosmeticDefVO toDefVO(CosmeticDef def) {
        CosmeticDefVO vo = new CosmeticDefVO();
        BeanUtils.copyProperties(def, vo);
        return vo;
    }

    /** 构造幂等发放接口的结果 VO。 */
    private CosmeticGrantResultVO buildGrantResult(String code, int quantity, boolean granted) {
        CosmeticGrantResultVO vo = new CosmeticGrantResultVO();
        vo.setCosmeticCode(code);
        vo.setQuantity(quantity);
        vo.setGranted(granted);
        return vo;
    }
}
