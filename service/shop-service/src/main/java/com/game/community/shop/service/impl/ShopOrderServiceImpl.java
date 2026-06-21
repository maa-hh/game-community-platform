package com.game.community.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.constant.shop.ShopConstants;
import com.game.community.common.constant.shop.ShopRedisConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.shop.CreateShopOrderDTO;
import com.game.community.model.dto.shop.PayShopOrderDTO;
import com.game.community.model.entity.shop.ShopCoupon;
import com.game.community.model.entity.shop.ShopItem;
import com.game.community.model.entity.shop.ShopOrder;
import com.game.community.model.entity.shop.ShopUserCoupon;
import com.game.community.model.message.ShopOrderCreateMessage;
import com.game.community.model.message.ShopOrderPaidMessage;
import com.game.community.model.vo.shop.ShopOrderVO;
import com.game.community.shop.mapper.ShopCouponMapper;
import com.game.community.shop.mapper.ShopItemMapper;
import com.game.community.shop.mapper.ShopOrderMapper;
import com.game.community.shop.mapper.ShopPurchaseLimitMapper;
import com.game.community.shop.mapper.ShopUserCouponMapper;
import com.game.community.shop.mapper.ShopUserCurrencyMapper;
import com.game.community.shop.service.ShopItemService;
import com.game.community.shop.service.ShopOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopOrderServiceImpl implements ShopOrderService {

    private static final DefaultRedisScript<List> PRE_DEDUCT_SCRIPT = new DefaultRedisScript<>("""
            local exist = redis.call('get', KEYS[3])
            if exist then
              return {2, exist}
            end
            local stock = redis.call('get', KEYS[1])
            if not stock then
              return {-1, ''}
            end
            local quantity = tonumber(ARGV[1])
            local limitCount = tonumber(ARGV[2])
            if limitCount >= 0 then
              local currentLimit = tonumber(redis.call('get', KEYS[2]) or '0')
              if currentLimit + quantity > limitCount then
                return {-2, ''}
              end
            end
            if stock ~= '-1' then
              if tonumber(stock) < quantity then
                return {0, ''}
              end
              redis.call('decrby', KEYS[1], quantity)
              redis.call('expire', KEYS[1], tonumber(ARGV[9]))
            end
            if limitCount >= 0 then
              redis.call('incrby', KEYS[2], quantity)
              redis.call('expire', KEYS[2], tonumber(ARGV[3]))
            end
            redis.call('set', KEYS[3], ARGV[4], 'EX', tonumber(ARGV[5]))
            redis.call('set', KEYS[5], ARGV[7], 'EX', tonumber(ARGV[6]))
            redis.call('rpush', KEYS[4], ARGV[8])
            return {1, ARGV[4]}
            """, List.class);

    private final ShopItemMapper itemMapper;

    private final ShopOrderMapper orderMapper;

    private final ShopPurchaseLimitMapper purchaseLimitMapper;

    private final ShopCouponMapper couponMapper;

    private final ShopUserCouponMapper userCouponMapper;

    private final ShopUserCurrencyMapper currencyMapper;

    private final ShopItemService itemService;

    private final StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper objectMapper;

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public ShopOrderVO createOrder(Long userId, CreateShopOrderDTO dto) {
        ShopItem item = validateItem(dto.getItemId());
        Integer quantity = dto.getQuantity() == null ? 1 : dto.getQuantity();
        if (quantity < 1 || quantity > 10) {
            throw new BusinessException("购买数量不合法");
        }
        validatePayType(dto.getPayType());
        validateCouponProduct(userId, item);
        itemService.syncStockToRedis(item.getId());
        syncLimitToRedis(userId, item);

        String requestId = dto.getRequestId().trim();
        String requestKey = ShopRedisConstants.REQUEST_KEY_PREFIX + userId + ":" + requestId;
        String existingOrderNo = stringRedisTemplate.opsForValue().get(requestKey);
        if (StringUtils.hasText(existingOrderNo)) {
            return getOrder(userId, existingOrderNo);
        }
        String orderNo = "SO" + UUID.randomUUID().toString().replace("-", "").substring(0, 28).toUpperCase();
        Integer originalPrice = item.getPrice() * quantity;
        CouponContext couponContext = resolveAndLockCoupon(userId, dto.getUserCouponId(), item, originalPrice, orderNo);
        Integer finalPrice = couponContext.finalPrice();
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(ShopConstants.ORDER_EXPIRE_MINUTES);

        ShopOrderVO creating = new ShopOrderVO();
        creating.setOrderNo(orderNo);
        creating.setRequestId(requestId);
        creating.setItemId(item.getId());
        creating.setItemName(item.getName());
        creating.setItemIcon(item.getIcon());
        creating.setProductType(item.getProductType());
        creating.setQuantity(quantity);
        creating.setPayType(dto.getPayType());
        creating.setOriginalPrice(originalPrice);
        creating.setDiscountAmount(couponContext.discountAmount());
        creating.setFinalPrice(finalPrice);
        creating.setCouponId(couponContext.couponId());
        creating.setUserCouponId(couponContext.userCouponId());
        creating.setBusinessCode(item.getBusinessCode());
        creating.setStatus(ShopConstants.ORDER_CREATING);
        creating.setStatusText(statusText(ShopConstants.ORDER_CREATING));
        creating.setCreateTime(LocalDateTime.now());
        creating.setExpireTime(expireTime);

        ShopOrderCreateMessage message = new ShopOrderCreateMessage();
        message.setOrderNo(orderNo);
        message.setRequestId(requestId);
        message.setUserId(userId);
        message.setItemId(item.getId());
        message.setQuantity(quantity);
        message.setPayType(dto.getPayType());
        message.setOriginalPrice(originalPrice);
        message.setDiscountAmount(couponContext.discountAmount());
        message.setFinalPrice(finalPrice);
        message.setCouponId(couponContext.couponId());
        message.setUserCouponId(couponContext.userCouponId());
        message.setBusinessCode(item.getBusinessCode());
        message.setExpireTime(expireTime);
        message.setAttempts(0);

        List<?> result = executePreDeduct(userId, requestId, item, quantity, creating, message);
        long code = asLong(result.get(0));
        String resultOrderNo = String.valueOf(result.get(1));
        if (code == 2) {
            unlockCouponIfNecessary(userId, couponContext.userCouponId(), orderNo);
            return getOrder(userId, resultOrderNo);
        }
        if (code == -1) {
            unlockCouponIfNecessary(userId, couponContext.userCouponId(), orderNo);
            throw new BusinessException("库存缓存未初始化，请稍后重试");
        }
        if (code == -2) {
            unlockCouponIfNecessary(userId, couponContext.userCouponId(), orderNo);
            throw new BusinessException("超过该商品限购次数");
        }
        if (code == 0) {
            unlockCouponIfNecessary(userId, couponContext.userCouponId(), orderNo);
            throw new BusinessException("库存不足");
        }
        return creating;
    }

    @Override
    public ShopOrderVO getOrder(Long userId, String orderNo) {
        ShopOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order != null) {
            if (!order.getUserId().equals(userId)) {
                throw new BusinessException("订单不存在");
            }
            return toVO(order);
        }
        String state = stringRedisTemplate.opsForValue().get(ShopRedisConstants.ORDER_STATE_KEY_PREFIX + orderNo);
        if (!StringUtils.hasText(state)) {
            throw new BusinessException("订单不存在");
        }
        try {
            ShopOrderVO vo = objectMapper.readValue(state, ShopOrderVO.class);
            return vo;
        } catch (JsonProcessingException e) {
            throw new BusinessException("订单状态解析失败");
        }
    }

    @Override
    public PageResult<ShopOrderVO> pageOrders(Long userId, Long page, Long size) {
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        Page<ShopOrder> result = orderMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<ShopOrder>()
                        .eq(ShopOrder::getUserId, userId)
                        .orderByDesc(ShopOrder::getCreateTime)
                        .orderByDesc(ShopOrder::getId));
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(), current, pageSize, result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processCreateMessage(ShopOrderCreateMessage message) {
        if (orderMapper.selectByOrderNo(message.getOrderNo()) != null) {
            return;
        }
        ShopItem item = validateItem(message.getItemId());
        if (item.getStock() != null && item.getStock() >= 0) {
            int affected = itemMapper.deductLimitedStock(item.getId(), message.getQuantity());
            if (affected == 0) {
                throw new BusinessException("数据库库存不足");
            }
        }
        purchaseLimitMapper.increase(message.getUserId(), message.getItemId(), message.getQuantity());

        ShopOrder order = new ShopOrder();
        order.setOrderNo(message.getOrderNo());
        order.setRequestId(message.getRequestId());
        order.setUserId(message.getUserId());
        order.setItemId(item.getId());
        order.setItemName(item.getName());
        order.setItemIcon(item.getIcon());
        order.setProductType(item.getProductType());
        order.setQuantity(message.getQuantity());
        order.setPayType(message.getPayType());
        order.setOriginalPrice(message.getOriginalPrice());
        order.setDiscountAmount(message.getDiscountAmount());
        order.setFinalPrice(message.getFinalPrice());
        order.setCouponId(message.getCouponId());
        order.setUserCouponId(message.getUserCouponId());
        order.setBusinessCode(message.getBusinessCode());
        order.setStatus(ShopConstants.ORDER_PENDING_PAY);
        order.setCreateTime(LocalDateTime.now());
        order.setExpireTime(message.getExpireTime());
        order.setUpdateTime(LocalDateTime.now());
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException ignored) {
            return;
        }
        stringRedisTemplate.opsForZSet().add(ShopRedisConstants.ORDER_EXPIRE_QUEUE, message.getOrderNo(),
                message.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        writeOrderState(toVO(order));
    }

    @Override
    public void markCreateFailed(ShopOrderCreateMessage message, String reason) {
        rollbackRedisReservation(message);
        if (orderMapper.selectByOrderNo(message.getOrderNo()) == null) {
            ShopOrder order = new ShopOrder();
            order.setOrderNo(message.getOrderNo());
            order.setRequestId(message.getRequestId());
            order.setUserId(message.getUserId());
            order.setItemId(message.getItemId());
            order.setItemName("创建失败");
            order.setItemIcon(null);
            order.setProductType(0);
            order.setQuantity(message.getQuantity());
            order.setPayType(message.getPayType());
            order.setOriginalPrice(message.getOriginalPrice());
            order.setDiscountAmount(message.getDiscountAmount());
            order.setFinalPrice(message.getFinalPrice());
            order.setCouponId(message.getCouponId());
            order.setUserCouponId(message.getUserCouponId());
            order.setBusinessCode(message.getBusinessCode());
            order.setStatus(ShopConstants.ORDER_FAILED);
            order.setFailReason(reason);
            order.setCreateTime(LocalDateTime.now());
            order.setExpireTime(message.getExpireTime());
            order.setUpdateTime(LocalDateTime.now());
            try {
                orderMapper.insert(order);
            } catch (DuplicateKeyException ignored) {
                // 幂等兜底：同一订单已由其他消费者写入。
            }
        }
        unlockCouponIfNecessary(message.getUserId(), message.getUserCouponId(), message.getOrderNo());
        ShopOrderVO failed = getOrderSilently(message.getOrderNo());
        if (failed != null) {
            writeOrderState(failed);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long userId, PayShopOrderDTO dto) {
        ShopOrder order = orderMapper.selectByOrderNo(dto.getOrderNo());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() == ShopConstants.ORDER_PAID || order.getStatus() == ShopConstants.ORDER_COMPLETED) {
            return;
        }
        if (order.getStatus() == ShopConstants.ORDER_CREATING) {
            throw new BusinessException("订单仍在创建中，请稍后重试");
        }
        if (order.getStatus() != ShopConstants.ORDER_PENDING_PAY) {
            throw new BusinessException("订单状态不允许支付");
        }
        if (LocalDateTime.now().isAfter(order.getExpireTime())) {
            cancelOrder(userId, order.getOrderNo());
            throw new BusinessException("订单已过期");
        }
        int deducted = order.getPayType() == ShopConstants.PAY_GOLD
                ? currencyMapper.deductGold(userId, order.getFinalPrice().longValue())
                : currencyMapper.deductDiamond(userId, order.getFinalPrice().longValue());
        if (deducted == 0) {
            throw new BusinessException(order.getPayType() == ShopConstants.PAY_GOLD ? "金币不足" : "钻石不足");
        }
        int paid = orderMapper.markPaid(order.getOrderNo(), userId, ShopConstants.ORDER_PENDING_PAY, ShopConstants.ORDER_PAID);
        if (paid == 0) {
            refundCurrency(userId, order.getPayType(), order.getFinalPrice().longValue());
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        markCouponUsedIfNecessary(order);
        stringRedisTemplate.opsForZSet().remove(ShopRedisConstants.ORDER_EXPIRE_QUEUE, order.getOrderNo());
        ShopItem item = itemMapper.selectById(order.getItemId());
        if (isCouponProduct(item)) {
            grantCouponProduct(userId, order, item);
            return;
        }
        publishPaidMessage(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long userId, String orderNo) {
        ShopOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != ShopConstants.ORDER_PENDING_PAY) {
            throw new BusinessException("只有待支付订单可以取消");
        }
        int updated = orderMapper.updateStatus(orderNo, userId, ShopConstants.ORDER_PENDING_PAY, ShopConstants.ORDER_CANCELLED);
        if (updated == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        restoreReservation(order);
        stringRedisTemplate.opsForZSet().remove(ShopRedisConstants.ORDER_EXPIRE_QUEUE, orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelExpiredOrders() {
        long now = System.currentTimeMillis();
        Set<String> orderNos = stringRedisTemplate.opsForZSet().rangeByScore(ShopRedisConstants.ORDER_EXPIRE_QUEUE, 0, now, 0, 50);
        if (orderNos == null || orderNos.isEmpty()) {
            return;
        }
        for (String orderNo : orderNos) {
            ShopOrder order = orderMapper.selectByOrderNo(orderNo);
            if (order == null || order.getStatus() != ShopConstants.ORDER_PENDING_PAY) {
                stringRedisTemplate.opsForZSet().remove(ShopRedisConstants.ORDER_EXPIRE_QUEUE, orderNo);
                continue;
            }
            int updated = orderMapper.updateStatus(orderNo, order.getUserId(), ShopConstants.ORDER_PENDING_PAY, ShopConstants.ORDER_CANCELLED);
            if (updated > 0) {
                restoreReservation(order);
            }
            stringRedisTemplate.opsForZSet().remove(ShopRedisConstants.ORDER_EXPIRE_QUEUE, orderNo);
        }
    }

    private List<?> executePreDeduct(Long userId,
                                     String requestId,
                                     ShopItem item,
                                     Integer quantity,
                                     ShopOrderVO creating,
                                     ShopOrderCreateMessage message) {
        try {
            return stringRedisTemplate.execute(PRE_DEDUCT_SCRIPT,
                    List.of(
                            ShopRedisConstants.STOCK_KEY_PREFIX + item.getId(),
                            ShopRedisConstants.LIMIT_KEY_PREFIX + item.getId() + ":" + userId,
                            ShopRedisConstants.REQUEST_KEY_PREFIX + userId + ":" + requestId,
                            ShopRedisConstants.ORDER_CREATE_QUEUE,
                            ShopRedisConstants.ORDER_STATE_KEY_PREFIX + creating.getOrderNo()
                    ),
                    String.valueOf(quantity),
                    String.valueOf(item.getLimitCount() == null ? ShopConstants.LIMIT_UNLIMITED : item.getLimitCount()),
                    String.valueOf(ShopRedisConstants.LIMIT_TTL_SECONDS),
                    creating.getOrderNo(),
                    String.valueOf(ShopRedisConstants.REQUEST_TTL_SECONDS),
                    String.valueOf(ShopRedisConstants.ORDER_STATE_TTL_SECONDS),
                    objectMapper.writeValueAsString(creating),
                    objectMapper.writeValueAsString(message),
                    String.valueOf(ShopRedisConstants.STOCK_TTL_SECONDS));
        } catch (JsonProcessingException e) {
            throw new BusinessException("订单请求序列化失败");
        }
    }

    private void syncLimitToRedis(Long userId, ShopItem item) {
        Integer limitCount = item.getLimitCount();
        if (limitCount == null || limitCount < 0) {
            return;
        }
        int reserved = Math.max(
                purchaseLimitMapper.selectPurchasedCount(userId, item.getId()),
                orderMapper.countActivePurchasedQuantity(userId, item.getId()));
        String key = ShopRedisConstants.LIMIT_KEY_PREFIX + item.getId() + ":" + userId;
        Boolean initialized = stringRedisTemplate.opsForValue().setIfAbsent(
                key,
                String.valueOf(reserved),
                Duration.ofSeconds(ShopRedisConstants.LIMIT_TTL_SECONDS));
        if (Boolean.FALSE.equals(initialized)) {
            String current = stringRedisTemplate.opsForValue().get(key);
            long currentCount = StringUtils.hasText(current) ? Long.parseLong(current) : 0L;
            if (reserved > currentCount) {
                stringRedisTemplate.opsForValue().set(key, String.valueOf(reserved), Duration.ofSeconds(ShopRedisConstants.LIMIT_TTL_SECONDS));
            }
        }
    }

    private ShopItem validateItem(Long itemId) {
        ShopItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException("商品不存在");
        }
        if (item.getStatus() == null || item.getStatus() != ShopConstants.ITEM_ON_SHELF) {
            throw new BusinessException("商品已下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (item.getBeginTime() != null && now.isBefore(item.getBeginTime())) {
            throw new BusinessException("商品尚未开始售卖");
        }
        if (item.getEndTime() != null && now.isAfter(item.getEndTime())) {
            throw new BusinessException("商品已结束售卖");
        }
        return item;
    }

    private void validatePayType(Integer payType) {
        if (payType == null || payType != ShopConstants.PAY_GOLD && payType != ShopConstants.PAY_DIAMOND) {
            throw new BusinessException("支付方式不合法");
        }
    }

    private void restoreReservation(ShopOrder order) {
        ShopItem item = itemMapper.selectById(order.getItemId());
        if (item != null && item.getStock() != null && item.getStock() >= 0) {
            itemMapper.restoreLimitedStock(order.getItemId(), order.getQuantity());
            incrementRedisStock(order.getItemId(), order.getQuantity());
        }
        purchaseLimitMapper.decrease(order.getUserId(), order.getItemId(), order.getQuantity());
        decreaseRedisLimit(order.getUserId(), order.getItemId(), order.getQuantity());
        unlockCouponIfNecessary(order.getUserId(), order.getUserCouponId(), order.getOrderNo());
    }

    private void rollbackRedisReservation(ShopOrderCreateMessage message) {
        ShopItem item = itemMapper.selectById(message.getItemId());
        if (item != null && item.getStock() != null && item.getStock() >= 0) {
            incrementRedisStock(message.getItemId(), message.getQuantity());
        }
        decreaseRedisLimit(message.getUserId(), message.getItemId(), message.getQuantity());
        unlockCouponIfNecessary(message.getUserId(), message.getUserCouponId(), message.getOrderNo());
    }

    private CouponContext resolveAndLockCoupon(Long userId, Long userCouponId, ShopItem item, Integer originalPrice, String orderNo) {
        if (userCouponId == null || userCouponId <= 0) {
            return new CouponContext(null, null, 0, originalPrice);
        }
        ShopUserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null || !userCoupon.getUserId().equals(userId)) {
            throw new BusinessException("用户优惠券不存在");
        }
        if (userCoupon.getStatus() != ShopConstants.COUPON_STATUS_UNUSED) {
            throw new BusinessException("优惠券不可用");
        }
        ShopCoupon coupon = couponMapper.selectById(userCoupon.getCouponId());
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        if (coupon.getExpireTime() != null && LocalDateTime.now().isAfter(coupon.getExpireTime())) {
            throw new BusinessException("优惠券已过期");
        }
        if (!isCouponApplicable(coupon, item)) {
            throw new BusinessException("优惠券不适用于该商品");
        }
        if (coupon.getMinAmount() != null && originalPrice < coupon.getMinAmount()) {
            throw new BusinessException("订单金额未达到优惠券使用门槛");
        }
        int discountAmount = calculateDiscount(coupon, originalPrice);
        int finalPrice = Math.max(0, originalPrice - discountAmount);
        int locked = userCouponMapper.lockCoupon(userCouponId, userId, orderNo);
        if (locked == 0) {
            throw new BusinessException("优惠券已被使用或锁定");
        }
        return new CouponContext(coupon.getId(), userCouponId, discountAmount, finalPrice);
    }

    private boolean isCouponApplicable(ShopCoupon coupon, ShopItem item) {
        if (coupon.getScopeItemId() != null) {
            return coupon.getScopeItemId().equals(item.getId());
        }
        if (coupon.getScopeProductType() != null) {
            return coupon.getScopeProductType().equals(item.getProductType());
        }
        Integer scopeType = coupon.getScopeType();
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

    private void validateCouponProduct(Long userId, ShopItem item) {
        if (!isCouponProduct(item)) {
            return;
        }
        if (item.getBusinessId() == null) {
            throw new BusinessException("优惠券商品未绑定优惠券模板");
        }
        ShopCoupon coupon = couponMapper.selectById(item.getBusinessId());
        if (coupon == null) {
            throw new BusinessException("优惠券商品绑定的优惠券不存在");
        }
        if (coupon.getExpireTime() != null && LocalDateTime.now().isAfter(coupon.getExpireTime())) {
            throw new BusinessException("优惠券商品已过期");
        }
        ShopUserCoupon existing = userCouponMapper.selectOne(new LambdaQueryWrapper<ShopUserCoupon>()
                .eq(ShopUserCoupon::getUserId, userId)
                .eq(ShopUserCoupon::getCouponId, item.getBusinessId()));
        if (existing != null
                && (existing.getStatus() == ShopConstants.COUPON_STATUS_UNUSED
                || existing.getStatus() == ShopConstants.COUPON_STATUS_LOCKED)) {
            throw new BusinessException("你已经拥有该优惠券，暂不能重复购买");
        }
    }

    private boolean isCouponProduct(ShopItem item) {
        return item != null && item.getProductType() != null && item.getProductType() == ShopConstants.PRODUCT_TYPE_COUPON;
    }

    private void grantCouponProduct(Long userId, ShopOrder order, ShopItem item) {
        Long couponId = item.getBusinessId();
        if (couponId == null) {
            throw new BusinessException("优惠券商品未绑定优惠券模板");
        }
        ShopUserCoupon existing = userCouponMapper.selectOne(new LambdaQueryWrapper<ShopUserCoupon>()
                .eq(ShopUserCoupon::getUserId, userId)
                .eq(ShopUserCoupon::getCouponId, couponId));
        if (existing == null) {
            ShopUserCoupon userCoupon = new ShopUserCoupon();
            userCoupon.setUserId(userId);
            userCoupon.setCouponId(couponId);
            userCoupon.setStatus(ShopConstants.COUPON_STATUS_UNUSED);
            userCoupon.setCreateTime(LocalDateTime.now());
            userCoupon.setOrderNo(order.getOrderNo());
            try {
                userCouponMapper.insert(userCoupon);
            } catch (DuplicateKeyException e) {
                ShopUserCoupon concurrent = userCouponMapper.selectOne(new LambdaQueryWrapper<ShopUserCoupon>()
                        .eq(ShopUserCoupon::getUserId, userId)
                        .eq(ShopUserCoupon::getCouponId, couponId));
                if (concurrent == null) {
                    throw e;
                }
                userCouponMapper.resetCoupon(concurrent.getId(), userId, order.getOrderNo());
            }
            return;
        }
        userCouponMapper.resetCoupon(existing.getId(), userId, order.getOrderNo());
    }

    private int calculateDiscount(ShopCoupon coupon, Integer originalPrice) {
        if (coupon.getDiscountType() == ShopConstants.COUPON_DISCOUNT_AMOUNT) {
            return Math.min(originalPrice, coupon.getDiscountValue());
        }
        if (coupon.getDiscountType() == ShopConstants.COUPON_DISCOUNT_RATE) {
            int rate = Math.max(0, Math.min(100, coupon.getDiscountValue()));
            return originalPrice - originalPrice * rate / 100;
        }
        throw new BusinessException("优惠券类型不合法");
    }

    private void unlockCouponIfNecessary(Long userId, Long userCouponId, String orderNo) {
        if (userCouponId != null) {
            userCouponMapper.unlockCoupon(userCouponId, userId, orderNo);
        }
    }

    private void markCouponUsedIfNecessary(ShopOrder order) {
        if (order.getUserCouponId() == null) {
            return;
        }
        int updated = userCouponMapper.markUsed(order.getUserCouponId(), order.getUserId(), order.getOrderNo());
        if (updated == 0) {
            throw new BusinessException("优惠券状态已变化，请刷新后重试");
        }
    }

    private void publishPaidMessage(ShopOrder order) {
        ShopOrderPaidMessage message = new ShopOrderPaidMessage();
        message.setOrderNo(order.getOrderNo());
        message.setUserId(order.getUserId());
        message.setItemId(order.getItemId());
        message.setProductType(order.getProductType());
        message.setPrice(order.getFinalPrice());
        message.setCouponId(order.getCouponId());
        message.setBusinessCode(order.getBusinessCode());
        message.setQuantity(order.getQuantity());
        try {
            kafkaTemplate.send(KafkaTopicConstants.SHOP_ORDER_PAID_TOPIC, order.getOrderNo(), objectMapper.writeValueAsString(message))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.warn("发送商城支付成功事件失败: orderNo={}", order.getOrderNo(), ex);
                        }
                    });
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("发送商城支付成功事件准备失败: orderNo={}", order.getOrderNo(), e);
        }
    }

    private void incrementRedisStock(Long itemId, Integer quantity) {
        String key = ShopRedisConstants.STOCK_KEY_PREFIX + itemId;
        String stock = stringRedisTemplate.opsForValue().get(key);
        if (stock != null && !String.valueOf(ShopConstants.STOCK_UNLIMITED).equals(stock)) {
            stringRedisTemplate.opsForValue().increment(key, quantity);
            stringRedisTemplate.expire(key, Duration.ofSeconds(ShopRedisConstants.STOCK_TTL_SECONDS));
        }
    }

    private void decreaseRedisLimit(Long userId, Long itemId, Integer quantity) {
        String key = ShopRedisConstants.LIMIT_KEY_PREFIX + itemId + ":" + userId;
        Long value = stringRedisTemplate.opsForValue().decrement(key, quantity);
        if (value == null || value <= 0) {
            stringRedisTemplate.delete(key);
        } else {
            stringRedisTemplate.expire(key, Duration.ofSeconds(ShopRedisConstants.LIMIT_TTL_SECONDS));
        }
    }

    private void refundCurrency(Long userId, Integer payType, Long amount) {
        if (payType == ShopConstants.PAY_GOLD) {
            currencyMapper.addGold(userId, amount);
        } else {
            currencyMapper.addDiamond(userId, amount);
        }
    }

    private void writeOrderState(ShopOrderVO vo) {
        try {
            stringRedisTemplate.opsForValue().set(ShopRedisConstants.ORDER_STATE_KEY_PREFIX + vo.getOrderNo(),
                    objectMapper.writeValueAsString(vo),
                    Duration.ofSeconds(ShopRedisConstants.ORDER_STATE_TTL_SECONDS));
        } catch (JsonProcessingException e) {
            log.warn("写入订单状态缓存失败: orderNo={}", vo.getOrderNo(), e);
        }
    }

    private ShopOrderVO getOrderSilently(String orderNo) {
        ShopOrder order = orderMapper.selectByOrderNo(orderNo);
        return order == null ? null : toVO(order);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private ShopOrderVO toVO(ShopOrder order) {
        ShopOrderVO vo = new ShopOrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setStatusText(statusText(order.getStatus()));
        return vo;
    }

    private String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case ShopConstants.ORDER_FAILED -> "创建失败";
            case ShopConstants.ORDER_CANCELLED -> "已取消";
            case ShopConstants.ORDER_CREATING -> "创建中";
            case ShopConstants.ORDER_PENDING_PAY -> "待支付";
            case ShopConstants.ORDER_PAID -> "已支付";
            case ShopConstants.ORDER_COMPLETED -> "已完成";
            default -> "未知";
        };
    }

    private record CouponContext(Long couponId, Long userCouponId, Integer discountAmount, Integer finalPrice) {
    }
}
