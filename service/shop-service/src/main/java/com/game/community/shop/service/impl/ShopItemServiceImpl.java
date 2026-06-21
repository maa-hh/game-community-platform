package com.game.community.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.constant.shop.ShopConstants;
import com.game.community.common.constant.shop.ShopRedisConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.shop.SaveShopItemDTO;
import com.game.community.model.entity.shop.ShopItem;
import com.game.community.model.vo.shop.ShopItemVO;
import com.game.community.shop.mapper.ShopItemMapper;
import com.game.community.shop.service.ShopItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopItemServiceImpl implements ShopItemService {

    private final ShopItemMapper itemMapper;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public PageResult<ShopItemVO> pageItems(Long page, Long size, Integer productType, Integer status) {
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 12 : Math.min(size, 100);
        Page<ShopItem> result = itemMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<ShopItem>()
                        .eq(productType != null, ShopItem::getProductType, productType)
                        .eq(status != null, ShopItem::getStatus, status)
                        .orderByDesc(ShopItem::getStatus)
                        .orderByDesc(ShopItem::getCreateTime)
                        .orderByDesc(ShopItem::getId));
        List<ShopItemVO> records = result.getRecords().stream().map(this::toVO).toList();
        records.forEach(item -> syncStockToRedis(item.getId()));
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public ShopItemVO getItem(Long itemId) {
        ShopItem item = requireItem(itemId);
        syncStockToRedis(itemId);
        return toVO(item);
    }

    @Override
    public ShopItemVO saveItem(SaveShopItemDTO dto) {
        ShopItem item = new ShopItem();
        BeanUtils.copyProperties(dto, item);
        item.setStock(dto.getStock() == null ? ShopConstants.STOCK_UNLIMITED : dto.getStock());
        item.setStatus(dto.getStatus() == null ? ShopConstants.ITEM_ON_SHELF : dto.getStatus());
        item.setQuantity(dto.getQuantity() == null || dto.getQuantity() < 1 ? 1 : dto.getQuantity());
        item.setLimitCount(dto.getLimitCount() == null ? ShopConstants.LIMIT_UNLIMITED : dto.getLimitCount());
        if (!StringUtils.hasText(item.getBusinessCode())) {
            item.setBusinessCode(null);
        }
        if (item.getId() == null) {
            item.setVersion(0);
            item.setCreateTime(LocalDateTime.now());
            item.setUpdateTime(LocalDateTime.now());
            itemMapper.insert(item);
        } else {
            item.setUpdateTime(LocalDateTime.now());
            itemMapper.updateById(item);
        }
        syncStockToRedis(item.getId());
        return toVO(requireItem(item.getId()));
    }

    @Override
    public void updateStatus(Long itemId, Integer status) {
        if (status == null || status != ShopConstants.ITEM_ON_SHELF && status != ShopConstants.ITEM_OFF_SHELF) {
            throw new BusinessException("商品状态不合法");
        }
        ShopItem item = requireItem(itemId);
        item.setStatus(status);
        item.setUpdateTime(LocalDateTime.now());
        itemMapper.updateById(item);
        syncStockToRedis(itemId);
    }

    @Override
    public void syncStockToRedis(Long itemId) {
        if (itemId == null) {
            return;
        }
        ShopItem item = itemMapper.selectById(itemId);
        if (item == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                ShopRedisConstants.STOCK_KEY_PREFIX + itemId,
                String.valueOf(item.getStock() == null ? ShopConstants.STOCK_UNLIMITED : item.getStock()),
                Duration.ofSeconds(ShopRedisConstants.STOCK_TTL_SECONDS));
    }

    private ShopItem requireItem(Long itemId) {
        ShopItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException("商品不存在");
        }
        return item;
    }

    private ShopItemVO toVO(ShopItem item) {
        ShopItemVO vo = new ShopItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }
}
