package com.game.community.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.constant.shop.ShopConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.PageResult;
import com.game.community.model.entity.shop.ShopCoupon;
import com.game.community.model.entity.shop.ShopItem;
import com.game.community.model.entity.shop.ShopUserCoupon;
import com.game.community.model.vo.shop.ShopCouponVO;
import com.game.community.shop.mapper.ShopCouponMapper;
import com.game.community.shop.mapper.ShopItemMapper;
import com.game.community.shop.mapper.ShopUserCouponMapper;
import com.game.community.shop.service.ShopCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopCouponServiceImpl implements ShopCouponService {

    private final ShopCouponMapper couponMapper;

    private final ShopUserCouponMapper userCouponMapper;

    private final ShopItemMapper itemMapper;

    @Override
    public PageResult<ShopCoupon> listCoupons(Long page, Long size) {
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        Page<ShopCoupon> result = couponMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<ShopCoupon>().orderByDesc(ShopCoupon::getCreateTime).orderByDesc(ShopCoupon::getId));
        return PageResult.of(result.getRecords(), current, pageSize, result.getTotal());
    }

    @Override
    public ShopCoupon getCoupon(Long couponId) {
        ShopCoupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        return coupon;
    }

    @Override
    public ShopCoupon saveCoupon(ShopCoupon coupon) {
        validateCoupon(coupon);
        coupon.setId(null);
        coupon.setCreateTime(LocalDateTime.now());
        coupon.setUpdateTime(LocalDateTime.now());
        couponMapper.insert(coupon);
        return couponMapper.selectById(coupon.getId());
    }

    @Override
    public ShopCoupon updateCoupon(ShopCoupon coupon) {
        if (coupon.getId() == null || couponMapper.selectById(coupon.getId()) == null) {
            throw new BusinessException("优惠券不存在");
        }
        validateCoupon(coupon);
        coupon.setUpdateTime(LocalDateTime.now());
        couponMapper.updateById(coupon);
        return couponMapper.selectById(coupon.getId());
    }

    @Override
    public void deleteCoupon(Long couponId) {
        couponMapper.deleteById(couponId);
    }

    @Override
    public PageResult<ShopCouponVO> listUserCoupons(Long userId, Long page, Long size, Integer status, Long itemId) {
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        Page<ShopUserCoupon> result = userCouponMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<ShopUserCoupon>()
                        .eq(ShopUserCoupon::getUserId, userId)
                        .eq(status != null, ShopUserCoupon::getStatus, status)
                        .orderByDesc(ShopUserCoupon::getCreateTime)
                        .orderByDesc(ShopUserCoupon::getId));
        ShopItem item = itemId == null ? null : itemMapper.selectById(itemId);
        List<ShopCouponVO> records = result.getRecords().stream()
                .map(userCoupon -> toVO(userCoupon, couponMapper.selectById(userCoupon.getCouponId())))
                .filter(vo -> vo.getCouponId() != null)
                .filter(vo -> item == null || isUsableForItem(vo, item))
                .toList();
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public ShopCouponVO getUserCoupon(Long userId, Long userCouponId) {
        ShopUserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null || !userCoupon.getUserId().equals(userId)) {
            throw new BusinessException("用户优惠券不存在");
        }
        return toVO(userCoupon, getCoupon(userCoupon.getCouponId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopCouponVO grantCoupon(Long userId, Long couponId) {
        ShopCoupon coupon = getCoupon(couponId);
        ShopUserCoupon userCoupon = new ShopUserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(couponId);
        userCoupon.setStatus(ShopConstants.COUPON_STATUS_UNUSED);
        userCoupon.setCreateTime(LocalDateTime.now());
        try {
            userCouponMapper.insert(userCoupon);
        } catch (DuplicateKeyException e) {
            ShopUserCoupon existing = userCouponMapper.selectOne(new LambdaQueryWrapper<ShopUserCoupon>()
                    .eq(ShopUserCoupon::getUserId, userId)
                    .eq(ShopUserCoupon::getCouponId, couponId));
            if (existing == null) {
                throw e;
            }
            return toVO(existing, coupon);
        }
        return toVO(userCoupon, coupon);
    }

    @Override
    public void deleteUserCoupon(Long userId, Long userCouponId) {
        userCouponMapper.delete(new LambdaQueryWrapper<ShopUserCoupon>()
                .eq(ShopUserCoupon::getId, userCouponId)
                .eq(ShopUserCoupon::getUserId, userId));
    }

    private void validateCoupon(ShopCoupon coupon) {
        if (coupon.getName() == null || coupon.getName().isBlank()) {
            throw new BusinessException("优惠券名称不能为空");
        }
        if (coupon.getDiscountType() == null
                || coupon.getDiscountType() != ShopConstants.COUPON_DISCOUNT_AMOUNT
                && coupon.getDiscountType() != ShopConstants.COUPON_DISCOUNT_RATE) {
            throw new BusinessException("优惠券类型不合法");
        }
        if (coupon.getDiscountValue() == null || coupon.getDiscountValue() <= 0) {
            throw new BusinessException("优惠券面额不合法");
        }
        if (coupon.getMinAmount() == null || coupon.getMinAmount() < 0) {
            coupon.setMinAmount(0);
        }
        if (coupon.getDiscountType() == ShopConstants.COUPON_DISCOUNT_RATE && coupon.getDiscountValue() > 100) {
            throw new BusinessException("折扣券不能超过100折扣点");
        }
        if (coupon.getScopeType() == null) {
            coupon.setScopeType(ShopConstants.COUPON_SCOPE_ALL);
        }
    }

    private boolean isApplicable(Integer scopeType, Long scopeItemId, Integer scopeProductType, ShopItem item) {
        if (item == null) {
            return false;
        }
        if (scopeItemId != null) {
            return scopeItemId.equals(item.getId());
        }
        if (scopeProductType != null) {
            return scopeProductType.equals(item.getProductType());
        }
        if (scopeType == null) {
            return false;
        }
        if (scopeType == ShopConstants.COUPON_SCOPE_ALL) {
            return true;
        }
        if (scopeType >= 0 && scopeType <= ShopConstants.PRODUCT_TYPE_ITEM) {
            return scopeType.equals(item.getProductType());
        }
        return scopeType.longValue() == item.getId();
    }

    private boolean isUsableForItem(ShopCouponVO coupon, ShopItem item) {
        if (coupon.getMinAmount() != null && item.getPrice() < coupon.getMinAmount()) {
            return false;
        }
        return isApplicable(coupon.getScopeType(), coupon.getScopeItemId(), coupon.getScopeProductType(), item);
    }

    private ShopCouponVO toVO(ShopUserCoupon userCoupon, ShopCoupon coupon) {
        ShopCouponVO vo = new ShopCouponVO();
        if (coupon != null) {
            vo.setCouponId(coupon.getId());
            vo.setCouponName(coupon.getName());
            vo.setDiscountType(coupon.getDiscountType());
            vo.setDiscountValue(coupon.getDiscountValue());
            vo.setMinAmount(coupon.getMinAmount());
            vo.setScopeType(coupon.getScopeType());
            vo.setScopeItemId(coupon.getScopeItemId());
            vo.setScopeProductType(coupon.getScopeProductType());
            vo.setExpireTime(coupon.getExpireTime());
        }
        if (userCoupon != null) {
            vo.setUserCouponId(userCoupon.getId());
            vo.setStatus(userCoupon.getStatus());
            vo.setStatusText(couponStatusText(userCoupon.getStatus()));
        }
        return vo;
    }

    private String couponStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case ShopConstants.COUPON_STATUS_UNUSED -> "未使用";
            case ShopConstants.COUPON_STATUS_USED -> "已使用";
            case ShopConstants.COUPON_STATUS_EXPIRED -> "已过期";
            case ShopConstants.COUPON_STATUS_LOCKED -> "已锁定";
            default -> "未知";
        };
    }
}
