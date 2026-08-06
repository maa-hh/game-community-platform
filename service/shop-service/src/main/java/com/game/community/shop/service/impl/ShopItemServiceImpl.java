package com.game.community.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.constant.shop.ShopConstants;
import com.game.community.common.constant.shop.ShopRedisConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.shop.SaveShopItemDTO;
import com.game.community.model.entity.shop.ShopItem;
import com.game.community.model.vo.cosmetic.CosmeticItemStateVO;
import com.game.community.model.vo.cosmetic.CosmeticPurchaseCheckVO;
import com.game.community.model.vo.shop.ShopItemVO;
import com.game.community.shop.mapper.ShopItemMapper;
import com.game.community.shop.service.ShopItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ShopItemServiceImpl implements ShopItemService {

    private static final LocalDateTime DEFAULT_BEGIN_TIME = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime DEFAULT_END_TIME = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    private final ShopItemMapper itemMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserFeignClient userFeignClient;

    @Override
    public PageResult<ShopItemVO> pageItems(Long userId, Long page, Long size, Integer status) {
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 12 : Math.min(size, 100);
        Page<ShopItem> result = itemMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<ShopItem>().eq(status != null, ShopItem::getStatus, status)
                        .orderByDesc(ShopItem::getStatus).orderByDesc(ShopItem::getCreateTime)
                        .orderByDesc(ShopItem::getId));
        return PageResult.of(result.getRecords().stream().map(item -> enrichItem(toVO(item), userId)).toList(),
                current, pageSize, result.getTotal());
    }

    @Override
    public ShopItemVO getItem(Long userId, Long itemId) {
        ShopItem item = requireItem(itemId);
        syncStockToRedis(itemId);
        return enrichItem(toVO(item), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopItemVO saveItem(SaveShopItemDTO dto) {
        validateDto(dto);
        ShopItem item = dto.getId() == null ? new ShopItem() : requireItem(dto.getId());
        Integer oldStock = item.getId() == null ? null : item.getStock();
        int newStock = dto.getStock() == null ? ShopConstants.STOCK_UNLIMITED : dto.getStock();
        if (item.getId() != null && ((oldStock < 0) != (newStock < 0))) {
            throw new BusinessException("已有订单的商品不能切换有限库存和不限库存");
        }
        int creatingReserved = item.getId() == null ? 0 : itemMapper.countCreatingQuantity(item.getId());
        if (newStock >= 0 && newStock < creatingReserved) {
            throw new BusinessException("新库存不能小于已预占库存");
        }
        LocalDateTime now = LocalDateTime.now();
        item.setName(dto.getName().trim());
        item.setDescription(defaultText(dto.getDescription()));
        item.setCosmeticCode(dto.getCosmeticCode().trim());
        item.setPricePoints(dto.getPricePoints());
        item.setGrantQuantity(dto.getGrantQuantity() == null ? 1 : dto.getGrantQuantity());
        item.setStock(newStock);
        item.setIcon(defaultText(dto.getIcon()));
        item.setStatus(dto.getStatus() == null ? ShopConstants.ITEM_ON_SHELF : dto.getStatus());
        item.setRepurchasePolicy(StringUtils.hasText(dto.getRepurchasePolicy())
                ? dto.getRepurchasePolicy() : ShopConstants.RepurchasePolicy.ONCE_FOREVER);
        item.setLimitCount(dto.getLimitCount() == null ? ShopConstants.LIMIT_UNLIMITED : dto.getLimitCount());
        item.setLimitWindowSeconds(dto.getLimitWindowSeconds() == null ? ShopConstants.LIMIT_WINDOW_DISABLED : dto.getLimitWindowSeconds());
        item.setBeginTime(dto.getBeginTime() == null ? DEFAULT_BEGIN_TIME : dto.getBeginTime());
        item.setEndTime(dto.getEndTime() == null ? DEFAULT_END_TIME : dto.getEndTime());
        item.setUpdateTime(now);
        if (item.getId() == null) {
            item.setVersion(0);
            item.setCreateTime(now);
            itemMapper.insert(item);
            syncStockToRedis(item.getId());
        } else {
            try {
                if (itemMapper.updateById(item) == 0) {
                    throw new OptimisticLockingFailureException("商品已被其他管理员修改");
                }
            } catch (OptimisticLockingFailureException e) {
                throw new BusinessException(e.getMessage());
            }
            adjustStockCache(item.getId(), oldStock, newStock);
        }
        return toVO(requireItem(item.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long itemId, Integer status) {
        if (status == null || (status != ShopConstants.ITEM_ON_SHELF && status != ShopConstants.ITEM_OFF_SHELF)) {
            throw new BusinessException("商品状态不合法");
        }
        ShopItem item = requireItem(itemId);
        item.setStatus(status);
        item.setUpdateTime(LocalDateTime.now());
        if (itemMapper.updateById(item) == 0) {
            throw new BusinessException("商品已被其他管理员修改");
        }
    }

    @Override
    public void syncStockToRedis(Long itemId) {
        if (itemId == null) return;
        ShopItem item = itemMapper.selectById(itemId);
        if (item == null) return;
        int available = item.getStock();
        if (available >= 0) {
            available = Math.max(0, available - itemMapper.countCreatingQuantity(itemId));
        }
        stringRedisTemplate.opsForValue().setIfAbsent(ShopRedisConstants.stockKey(itemId),
                String.valueOf(available), Duration.ofSeconds(ShopRedisConstants.STOCK_TTL_SECONDS));
    }

    private void adjustStockCache(Long itemId, Integer oldStock, int newStock) {
        if (oldStock == null || oldStock < 0 || newStock < 0) {
            syncStockToRedis(itemId);
            return;
        }
        syncStockToRedis(itemId);
        long delta = (long) newStock - oldStock;
        if (delta != 0) {
            stringRedisTemplate.opsForValue().increment(ShopRedisConstants.stockKey(itemId), delta);
        }
    }

    private ShopItemVO enrichItem(ShopItemVO vo, Long userId) {
        vo.setCanBuy(true);
        if (userId == null) return vo;
        Result<CosmeticItemStateVO> state = userFeignClient.getCosmeticItemState(userId, vo.getCosmeticCode());
        if (state == null || state.getData() == null) {
            vo.setCanBuy(false);
            vo.setCannotBuyReason("装扮状态服务暂不可用");
            return vo;
        }
        vo.setOwned(Boolean.TRUE.equals(state.getData().getOwned()));
        vo.setEquipped(Boolean.TRUE.equals(state.getData().getEquipped()));
        if (ShopConstants.RepurchasePolicy.ONCE_FOREVER.equals(vo.getRepurchasePolicy()) && vo.getOwned()) {
            vo.setCanBuy(false);
            vo.setCannotBuyReason("已拥有该装扮");
        }
        if (ShopConstants.RepurchasePolicy.ONCE_FOREVER.equals(vo.getRepurchasePolicy())) {
            Result<CosmeticPurchaseCheckVO> check = userFeignClient.checkCosmeticOwnership(userId, vo.getCosmeticCode());
            if (check != null && check.getData() != null) {
                vo.setCanBuy(check.getData().getCanBuy());
                vo.setCannotBuyReason(check.getData().getReason());
                vo.setNextBuyAt(check.getData().getNextBuyAt());
            }
        }
        return vo;
    }

    private void validateDto(SaveShopItemDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getName()) || !StringUtils.hasText(dto.getCosmeticCode())
                || dto.getPricePoints() == null || dto.getPricePoints() < 0) {
            throw new BusinessException("商品参数不完整");
        }
        int stock = dto.getStock() == null ? -1 : dto.getStock();
        int grant = dto.getGrantQuantity() == null ? 1 : dto.getGrantQuantity();
        int limit = dto.getLimitCount() == null ? -1 : dto.getLimitCount();
        int window = dto.getLimitWindowSeconds() == null ? 0 : dto.getLimitWindowSeconds();
        if (stock < -1 || grant < 1 || limit < -1 || window < 0) {
            throw new BusinessException("商品数量参数不合法");
        }
        if (limit == 0) {
            throw new BusinessException("限购次数不能为0");
        }
        String policy = dto.getRepurchasePolicy();
        if (!StringUtils.hasText(policy)) policy = ShopConstants.RepurchasePolicy.ONCE_FOREVER;
        if (!ShopConstants.RepurchasePolicy.ONCE_FOREVER.equals(policy)
                && !ShopConstants.RepurchasePolicy.UNLIMITED.equals(policy)
                && !ShopConstants.RepurchasePolicy.COOLDOWN.equals(policy)
                && !ShopConstants.RepurchasePolicy.LIMIT_PER_WINDOW.equals(policy)) {
            throw new BusinessException("复购策略不合法");
        }
        if ((ShopConstants.RepurchasePolicy.COOLDOWN.equals(policy)
                || ShopConstants.RepurchasePolicy.LIMIT_PER_WINDOW.equals(policy)) && window <= 0) {
            throw new BusinessException("该复购策略必须设置有效时间窗口");
        }
        if (ShopConstants.RepurchasePolicy.LIMIT_PER_WINDOW.equals(policy) && limit < 1) {
            throw new BusinessException("窗口限购次数必须大于0");
        }
        if (dto.getBeginTime() != null && dto.getEndTime() != null && dto.getBeginTime().isAfter(dto.getEndTime())) {
            throw new BusinessException("商品开始时间不能晚于结束时间");
        }
    }

    private ShopItem requireItem(Long itemId) {
        ShopItem item = itemMapper.selectById(itemId);
        if (item == null) throw new BusinessException("商品不存在");
        return item;
    }

    private ShopItemVO toVO(ShopItem item) {
        ShopItemVO vo = new ShopItemVO();
        BeanUtils.copyProperties(item, vo);
        vo.setDescription(defaultText(vo.getDescription()));
        vo.setIcon(defaultText(vo.getIcon()));
        return vo;
    }

    private String defaultText(String value) {
        return value == null ? ShopConstants.EMPTY_TEXT : value;
    }
}
