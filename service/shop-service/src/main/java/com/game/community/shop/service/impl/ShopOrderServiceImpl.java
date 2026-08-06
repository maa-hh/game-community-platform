package com.game.community.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.shop.ShopConstants;
import com.game.community.common.constant.shop.ShopRedisConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.cosmetic.GrantCosmeticDTO;
import com.game.community.model.dto.shop.CreateShopOrderDTO;
import com.game.community.model.dto.shop.ExchangeShopItemDTO;
import com.game.community.model.dto.shop.PayShopOrderDTO;
import com.game.community.model.entity.shop.ShopDeliveryTask;
import com.game.community.model.entity.shop.ShopItem;
import com.game.community.model.entity.shop.ShopOrder;
import com.game.community.model.entity.shop.ShopPurchaseLimit;
import com.game.community.model.vo.cosmetic.CosmeticGrantResultVO;
import com.game.community.model.vo.cosmetic.CosmeticPurchaseCheckVO;
import com.game.community.model.vo.shop.ExchangeShopResultVO;
import com.game.community.model.vo.shop.ShopOrderVO;
import com.game.community.shop.mapper.ShopDeliveryTaskMapper;
import com.game.community.shop.mapper.ShopItemMapper;
import com.game.community.shop.mapper.ShopOrderMapper;
import com.game.community.shop.mapper.ShopPurchaseLimitMapper;
import com.game.community.shop.service.ShopCurrencyService;
import com.game.community.shop.service.ShopOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

    private static final String POLICY_ONCE = ShopConstants.RepurchasePolicy.ONCE_FOREVER;
    private static final String POLICY_UNLIMITED = ShopConstants.RepurchasePolicy.UNLIMITED;
    private static final String POLICY_COOLDOWN = ShopConstants.RepurchasePolicy.COOLDOWN;
    private static final String POLICY_WINDOW = ShopConstants.RepurchasePolicy.LIMIT_PER_WINDOW;
    private static final String EPOCH_TIME = ShopConstants.EPOCH_TIME;

    private static final DefaultRedisScript<List> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            local stock = redis.call('get', KEYS[1])
            if not stock then
              return {-1, ''}
            end
            local quantity = tonumber(ARGV[1])
            local policy = ARGV[2]
            local limitCount = tonumber(ARGV[3])
            local windowSeconds = tonumber(ARGV[4])
            local now = tonumber(ARGV[5])
            local current = tonumber(redis.call('get', KEYS[2]) or '0')
            local reservationWindow = now
            if policy == 'ONCE_FOREVER' then
              if current + quantity > 1 then return {-2, ''} end
            elseif policy == 'LIMIT_PER_WINDOW' then
              local windowStart = tonumber(redis.call('get', KEYS[4]) or '0')
              if windowStart == 0 or now >= windowStart + windowSeconds * 1000 then
                current = 0
                windowStart = now
                redis.call('set', KEYS[4], now, 'EX', tonumber(ARGV[8]))
              end
              reservationWindow = windowStart
              if limitCount < 0 or current + quantity > limitCount then return {-2, ''} end
            elseif policy == 'COOLDOWN' then
              local last = tonumber(redis.call('get', KEYS[3]) or '0')
              if last > 0 and now < last + windowSeconds * 1000 then return {-3, ''} end
            end
            if stock ~= '-1' then
              if tonumber(stock) < quantity then return {0, ''} end
              redis.call('decrby', KEYS[1], quantity)
              redis.call('expire', KEYS[1], tonumber(ARGV[9]))
            end
            if policy ~= 'UNLIMITED' then
              if policy == 'COOLDOWN' then
                redis.call('set', KEYS[3], now, 'EX', tonumber(ARGV[8]))
              else
                redis.call('set', KEYS[2], current + quantity, 'EX', tonumber(ARGV[8]))
              end
            end
            redis.call('set', KEYS[5], reservationWindow, 'EX', tonumber(ARGV[7]))
            return {1, ARGV[6]}
            """, List.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local stock = redis.call('get', KEYS[1])
            if stock and stock ~= '-1' then
              redis.call('incrby', KEYS[1], tonumber(ARGV[1]))
              redis.call('expire', KEYS[1], tonumber(ARGV[4]))
            end
            local policy = ARGV[2]
            local reservationWindow = redis.call('get', KEYS[5])
            local currentWindow = redis.call('get', KEYS[4])
            if policy == 'COOLDOWN' then
              if reservationWindow and redis.call('get', KEYS[3]) == reservationWindow then
                redis.call('del', KEYS[3])
              end
            elseif policy ~= 'UNLIMITED' then
              if reservationWindow and currentWindow and reservationWindow == currentWindow then
                local current = tonumber(redis.call('get', KEYS[2]) or '0') - tonumber(ARGV[1])
                if current <= 0 then redis.call('del', KEYS[2]) else redis.call('set', KEYS[2], current, 'EX', tonumber(ARGV[3])) end
              end
            end
            redis.call('del', KEYS[5])
            return 1
            """, Long.class);

    private final ShopItemMapper itemMapper;
    private final ShopOrderMapper orderMapper;
    private final ShopPurchaseLimitMapper purchaseLimitMapper;
    private final ShopDeliveryTaskMapper deliveryTaskMapper;
    private final ShopCurrencyService currencyService;
    private final UserFeignClient userFeignClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ShopOrderVO createOrder(Long userId, CreateShopOrderDTO dto) {
        validateUserId(userId);
        String requestId = normalizeRequestId(dto.getRequestId());
        ShopOrder existing = orderMapper.selectByUserAndRequest(userId, requestId);
        if (existing != null) {
            return toVO(existing);
        }

        ShopItem item = validateItem(dto.getItemId());
        int quantity = normalizeQuantity(dto.getQuantity());
        validateRepurchase(userId, item);
        long pricePoints = item.getPricePoints();
        long totalPoints = multiply(pricePoints, quantity, "积分总价超出范围");
        int grantQuantity;
        try {
            grantQuantity = Math.multiplyExact(defaultGrantQuantity(item), quantity);
        } catch (ArithmeticException e) {
            throw new BusinessException("发放数量超出范围");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusMinutes(ShopConstants.ORDER_EXPIRE_MINUTES);
        String orderNo = generateOrderNo();
        ShopOrder order = buildCreatingOrder(orderNo, requestId, userId, item, quantity, pricePoints,
                totalPoints, grantQuantity, now, expireTime);

        syncStockToRedis(item.getId());
        syncLimitToRedis(userId, item);
        List<?> result = reserveRedis(userId, item, quantity, order);
        long code = asLong(result.get(0));
        if (code == -1) {
            throw new BusinessException("库存缓存未初始化，请稍后重试");
        }
        if (code == -2) {
            throw new BusinessException("超过该商品限购次数");
        }
        if (code == -3) {
            throw new BusinessException("购买冷却中，请稍后再试");
        }
        if (code == 0) {
            throw new BusinessException("库存不足");
        }

        try {
            orderMapper.insert(order);
            writeOrderState(toVO(order), userId);
            return toVO(order);
        } catch (DuplicateKeyException e) {
            releaseRedisReservation(userId, item, quantity, orderNo);
            ShopOrder duplicate = orderMapper.selectByUserAndRequest(userId, requestId);
            if (duplicate == null) {
                throw new BusinessException("订单幂等处理失败，请稍后重试");
            }
            return toVO(duplicate);
        } catch (RuntimeException e) {
            releaseRedisReservation(userId, item, quantity, orderNo);
            throw e;
        }
    }

    @Override
    public ExchangeShopResultVO exchange(Long userId, ExchangeShopItemDTO dto) {
        CreateShopOrderDTO createDto = new CreateShopOrderDTO();
        createDto.setItemId(dto.getItemId());
        createDto.setQuantity(dto.getQuantity());
        createDto.setRequestId(dto.getRequestId());
        ShopOrderVO order = createOrder(userId, createDto);
        if (order.getStatus() == ShopConstants.ORDER_PENDING_PAY) {
            PayShopOrderDTO payDto = new PayShopOrderDTO();
            payDto.setOrderNo(order.getOrderNo());
            payOrder(userId, payDto);
            order = getOrder(userId, order.getOrderNo());
        }
        ExchangeShopResultVO result = new ExchangeShopResultVO();
        result.setOrderNo(order.getOrderNo());
        result.setCosmeticCode(order.getCosmeticCode());
        result.setGrantQuantity(order.getGrantQuantity());
        result.setStatus(order.getStatus());
        result.setStatusText(order.getStatusText());
        result.setPointsBalance(currencyService.getOrCreate(userId).getPoints());
        return result;
    }

    @Override
    public ShopOrderVO getOrder(Long userId, String orderNo) {
        validateUserId(userId);
        ShopOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order != null) {
            if (!userId.equals(order.getUserId())) {
                throw new BusinessException("订单不存在");
            }
            return toVO(order);
        }
        String state = stringRedisTemplate.opsForValue().get(ShopRedisConstants.ORDER_STATE_KEY_PREFIX + orderNo);
        if (!StringUtils.hasText(state)) {
            throw new BusinessException("订单不存在");
        }
        try {
            JsonNode root = objectMapper.readTree(state);
            if (!userId.equals(root.path("userId").asLong())) {
                throw new BusinessException("订单不存在");
            }
            return objectMapper.treeToValue(root.path("order"), ShopOrderVO.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("订单状态解析失败");
        }
    }

    @Override
    public PageResult<ShopOrderVO> pageOrders(Long userId, Long page, Long size) {
        validateUserId(userId);
        long current = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 10 : Math.min(size, 100);
        Page<ShopOrder> result = orderMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<ShopOrder>().eq(ShopOrder::getUserId, userId)
                        .orderByDesc(ShopOrder::getCreateTime).orderByDesc(ShopOrder::getId));
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(), current, pageSize, result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processCreatingOrder(String orderNo) {
        ShopOrder order = orderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null || order.getStatus() != ShopConstants.ORDER_CREATING) {
            return;
        }
        ShopItem item = itemMapper.selectById(order.getItemId());
        if (item == null || !StringUtils.hasText(item.getCosmeticCode())) {
            throw new BusinessException("商品已不存在");
        }
        if (item.getStock() >= 0 && itemMapper.deductLimitedStock(item.getId(), order.getQuantity()) == 0) {
            throw new BusinessException("数据库库存不足");
        }
        reserveDatabase(order, item);
        if (orderMapper.updateStatus(order.getOrderNo(), order.getUserId(), ShopConstants.ORDER_CREATING,
                ShopConstants.ORDER_PENDING_PAY) == 0) {
            throw new BusinessException("订单状态更新失败");
        }
        addExpireQueue(order);
        order.setStatus(ShopConstants.ORDER_PENDING_PAY);
        writeOrderState(toVO(order), order.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markCreatingFailed(String orderNo, String reason) {
        ShopOrder order = orderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null || order.getStatus() != ShopConstants.ORDER_CREATING) {
            return;
        }
        if (orderMapper.markCreateFailed(orderNo, ShopConstants.ORDER_CREATING, ShopConstants.ORDER_FAILED,
                defaultText(reason)) > 0) {
            releaseRedisReservation(order.getUserId(), itemMapper.selectById(order.getItemId()),
                    order.getQuantity(), order.getOrderNo());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processDeliveryTask(ShopDeliveryTask task, String token) {
        ShopOrder order = orderMapper.selectByOrderNo(task.getOrderNo());
        if (order == null) {
            deliveryTaskMapper.markFailed(task.getId(), token, "订单不存在");
            return;
        }
        if (order.getStatus() == ShopConstants.ORDER_COMPLETED) {
            deliveryTaskMapper.markSent(task.getId(), token);
            return;
        }
        if (order.getStatus() != ShopConstants.ORDER_PAID) {
            deliveryTaskMapper.markFailed(task.getId(), token, "订单状态不允许发货");
            return;
        }
        grantCosmetic(order);
        if (deliveryTaskMapper.markSent(task.getId(), token) == 0) {
            return;
        }
        if (orderMapper.markCompleted(order.getOrderNo(), order.getUserId(), ShopConstants.ORDER_PAID,
                ShopConstants.ORDER_COMPLETED) == 0) {
            throw new BusinessException("订单完成状态更新失败");
        }
        completeLimit(order);
    }

    @Override
    public void markDeliveryFailed(ShopDeliveryTask task, String token, String reason) {
        deliveryTaskMapper.markFailed(task.getId(), token,
                StringUtils.hasText(reason) ? reason : "权益发放失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long userId, PayShopOrderDTO dto) {
        ShopOrder order = orderMapper.selectByOrderNoForUpdate(dto.getOrderNo());
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() == ShopConstants.ORDER_COMPLETED) {
            return;
        }
        if (order.getStatus() == ShopConstants.ORDER_PAID) {
            deliveryTaskMapper.insertIfAbsent(order.getOrderNo(), LocalDateTime.now());
            return;
        }
        if (order.getStatus() != ShopConstants.ORDER_PENDING_PAY) {
            throw new BusinessException("订单状态不允许支付");
        }
        if (LocalDateTime.now().isAfter(order.getExpireTime())) {
            cancelOrder(userId, order.getOrderNo());
            throw new BusinessException("订单已过期");
        }
        currencyService.deductPoints(userId, order.getTotalPoints().longValue(),
                ShopConstants.PointsBizType.SHOP_EXCHANGE, order.getOrderNo(), "商城兑换");
        if (orderMapper.markPaid(order.getOrderNo(), userId, ShopConstants.ORDER_PENDING_PAY,
                ShopConstants.ORDER_PAID) == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        deliveryTaskMapper.insertIfAbsent(order.getOrderNo(), LocalDateTime.now());
        stringRedisTemplate.opsForZSet().remove(ShopRedisConstants.ORDER_EXPIRE_QUEUE, order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long userId, String orderNo) {
        ShopOrder order = orderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != ShopConstants.ORDER_PENDING_PAY) {
            throw new BusinessException("只有待支付订单可以取消");
        }
        if (orderMapper.updateStatus(orderNo, userId, ShopConstants.ORDER_PENDING_PAY,
                ShopConstants.ORDER_CANCELLED) == 0) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        restoreDatabaseReservation(order);
        releaseRedisReservation(order.getUserId(), itemMapper.selectById(order.getItemId()),
                order.getQuantity(), order.getOrderNo());
        stringRedisTemplate.opsForZSet().remove(ShopRedisConstants.ORDER_EXPIRE_QUEUE, orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelExpiredOrders() {
        long now = System.currentTimeMillis();
        Set<String> orderNos = stringRedisTemplate.opsForZSet().rangeByScore(
                ShopRedisConstants.ORDER_EXPIRE_QUEUE, 0, now, 0, 100);
        if (orderNos == null) {
            return;
        }
        for (String orderNo : orderNos) {
            ShopOrder order = orderMapper.selectByOrderNoForUpdate(orderNo);
            if (order == null || order.getStatus() != ShopConstants.ORDER_PENDING_PAY) {
                stringRedisTemplate.opsForZSet().remove(ShopRedisConstants.ORDER_EXPIRE_QUEUE, orderNo);
                continue;
            }
            if (orderMapper.updateStatus(orderNo, order.getUserId(), ShopConstants.ORDER_PENDING_PAY,
                    ShopConstants.ORDER_CANCELLED) > 0) {
                restoreDatabaseReservation(order);
                releaseRedisReservation(order.getUserId(), itemMapper.selectById(order.getItemId()),
                        order.getQuantity(), order.getOrderNo());
            }
            stringRedisTemplate.opsForZSet().remove(ShopRedisConstants.ORDER_EXPIRE_QUEUE, orderNo);
        }
    }

    private void reserveDatabase(ShopOrder order, ShopItem item) {
        String policy = normalizePolicy(item.getRepurchasePolicy());
        if (POLICY_UNLIMITED.equals(policy)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime epoch = LocalDateTime.parse(EPOCH_TIME);
        purchaseLimitMapper.insertIfAbsent(order.getUserId(), item.getId(), epoch,
                POLICY_WINDOW.equals(policy) ? now : epoch);
        ShopPurchaseLimit limit = purchaseLimitMapper.selectForUpdate(order.getUserId(), item.getId());
        int purchased = safeCount(limit.getPurchasedCount());
        int reserved = safeCount(limit.getReservedCount());
        if (POLICY_WINDOW.equals(policy) && limit.getWindowStartAt().plusSeconds(item.getLimitWindowSeconds()).isBefore(now)) {
            purchased = 0;
            reserved = 0;
            limit.setWindowStartAt(now);
        }
        int allowed = POLICY_ONCE.equals(policy) ? 1 : item.getLimitCount();
        if (POLICY_ONCE.equals(policy) && purchased + reserved + order.getQuantity() > allowed) {
            throw new BusinessException("该装扮仅限购买一次");
        }
        if (POLICY_WINDOW.equals(policy) && (allowed < 0 || purchased + reserved + order.getQuantity() > allowed)) {
            throw new BusinessException("当前限购窗口内购买次数已达上限");
        }
        if (POLICY_COOLDOWN.equals(policy)
                && (reserved > 0 || limit.getLastPurchaseAt().plusSeconds(item.getLimitWindowSeconds()).isAfter(now))) {
            throw new BusinessException("购买冷却中，请稍后再试");
        }
        limit.setPurchasedCount(purchased);
        limit.setReservedCount(Math.addExact(reserved, order.getQuantity()));
        if (POLICY_COOLDOWN.equals(policy)) {
            limit.setLastPurchaseAt(now);
        }
        purchaseLimitMapper.updateById(limit);
    }

    private void completeLimit(ShopOrder order) {
        ShopItem item = itemMapper.selectById(order.getItemId());
        if (item == null || POLICY_UNLIMITED.equals(normalizePolicy(item.getRepurchasePolicy()))) {
            return;
        }
        ShopPurchaseLimit limit = purchaseLimitMapper.selectForUpdate(order.getUserId(), order.getItemId());
        if (limit == null) {
            return;
        }
        limit.setReservedCount(Math.max(0, safeCount(limit.getReservedCount()) - order.getQuantity()));
        limit.setPurchasedCount(Math.addExact(safeCount(limit.getPurchasedCount()), order.getQuantity()));
        purchaseLimitMapper.updateById(limit);
    }

    private void restoreDatabaseReservation(ShopOrder order) {
        ShopItem item = itemMapper.selectById(order.getItemId());
        if (item != null && item.getStock() >= 0) {
            itemMapper.restoreLimitedStock(order.getItemId(), order.getQuantity());
        }
        if (item == null || POLICY_UNLIMITED.equals(normalizePolicy(item.getRepurchasePolicy()))) {
            return;
        }
        ShopPurchaseLimit limit = purchaseLimitMapper.selectForUpdate(order.getUserId(), order.getItemId());
        if (limit == null) {
            return;
        }
        limit.setReservedCount(Math.max(0, safeCount(limit.getReservedCount()) - order.getQuantity()));
        if (POLICY_COOLDOWN.equals(normalizePolicy(item.getRepurchasePolicy())) && limit.getReservedCount() == 0) {
            limit.setLastPurchaseAt(LocalDateTime.parse(EPOCH_TIME));
        }
        purchaseLimitMapper.updateById(limit);
    }

    private void validateRepurchase(Long userId, ShopItem item) {
        if (!POLICY_ONCE.equals(normalizePolicy(item.getRepurchasePolicy()))) {
            return;
        }
        Result<CosmeticPurchaseCheckVO> check = userFeignClient.checkCosmeticOwnership(userId, item.getCosmeticCode());
        if (check == null || check.getData() == null || !Boolean.TRUE.equals(check.getData().getCanBuy())) {
            String reason = check != null && check.getData() != null ? check.getData().getReason() : "装扮状态校验失败";
            throw new BusinessException(StringUtils.hasText(reason) ? reason : "不可购买");
        }
    }

    private ShopItem validateItem(Long itemId) {
        if (itemId == null || itemId <= 0) {
            throw new BusinessException("商品ID不合法");
        }
        ShopItem item = itemMapper.selectById(itemId);
        if (item == null || item.getStatus() != ShopConstants.ITEM_ON_SHELF) {
            throw new BusinessException("商品不存在或已下架");
        }
        if (!StringUtils.hasText(item.getCosmeticCode())) {
            throw new BusinessException("商品未绑定装扮");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(item.getBeginTime()) || now.isAfter(item.getEndTime())) {
            throw new BusinessException("商品当前不可购买");
        }
        return item;
    }

    private List<?> reserveRedis(Long userId, ShopItem item, int quantity, ShopOrder order) {
        String policy = normalizePolicy(item.getRepurchasePolicy());
        return stringRedisTemplate.execute(RESERVE_SCRIPT, List.of(
                            ShopRedisConstants.stockKey(item.getId()),
                            ShopRedisConstants.limitKey(item.getId(), userId),
                            ShopRedisConstants.limitLastKey(item.getId(), userId),
                            ShopRedisConstants.limitWindowKey(item.getId(), userId),
                            ShopRedisConstants.limitReservationKey(item.getId(), userId, order.getOrderNo())),
                    String.valueOf(quantity), policy,
                    String.valueOf(item.getLimitCount()), String.valueOf(item.getLimitWindowSeconds()),
                    String.valueOf(System.currentTimeMillis()), order.getOrderNo(),
                    String.valueOf(ShopRedisConstants.RESERVATION_TTL_SECONDS),
                    String.valueOf(ShopRedisConstants.LIMIT_TTL_SECONDS),
                    String.valueOf(ShopRedisConstants.STOCK_TTL_SECONDS));
    }

    private void releaseRedisReservation(Long userId, ShopItem item, int quantity, String orderNo) {
        if (item == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(
                            ShopRedisConstants.stockKey(item.getId()),
                            ShopRedisConstants.limitKey(item.getId(), userId),
                            ShopRedisConstants.limitLastKey(item.getId(), userId),
                            ShopRedisConstants.limitWindowKey(item.getId(), userId),
                            ShopRedisConstants.limitReservationKey(item.getId(), userId, orderNo)),
                    String.valueOf(quantity), normalizePolicy(item.getRepurchasePolicy()),
                    String.valueOf(ShopRedisConstants.LIMIT_TTL_SECONDS),
                    String.valueOf(ShopRedisConstants.STOCK_TTL_SECONDS));
        } catch (RuntimeException e) {
            log.error("商城 Redis 预占回滚失败，需要执行库存对账: orderNo={}", orderNo, e);
        }
    }

    private void syncStockToRedis(Long itemId) {
        ShopItem item = itemMapper.selectById(itemId);
        if (item == null) {
            return;
        }
        int stock = item.getStock();
        if (stock >= 0) {
            stock = Math.max(0, stock - itemMapper.countCreatingQuantity(itemId));
        }
        stringRedisTemplate.opsForValue().setIfAbsent(ShopRedisConstants.stockKey(itemId),
                String.valueOf(stock), Duration.ofSeconds(ShopRedisConstants.STOCK_TTL_SECONDS));
    }

    private void syncLimitToRedis(Long userId, ShopItem item) {
        String policy = normalizePolicy(item.getRepurchasePolicy());
        if (POLICY_UNLIMITED.equals(policy)) {
            return;
        }
        ShopPurchaseLimit limit = purchaseLimitMapper.selectByUserAndItem(userId, item.getId());
        int creating = orderMapper.countCreatingQuantity(userId, item.getId());
        String count = String.valueOf((limit == null ? 0 : safeCount(limit.getPurchasedCount()) + safeCount(limit.getReservedCount())) + creating);
        stringRedisTemplate.opsForValue().setIfAbsent(ShopRedisConstants.limitKey(item.getId(), userId), count,
                Duration.ofSeconds(ShopRedisConstants.LIMIT_TTL_SECONDS));
        if (limit != null) {
            stringRedisTemplate.opsForValue().setIfAbsent(ShopRedisConstants.limitLastKey(item.getId(), userId),
                    String.valueOf(toEpochMillis(limit.getLastPurchaseAt())), Duration.ofSeconds(ShopRedisConstants.LIMIT_TTL_SECONDS));
            stringRedisTemplate.opsForValue().setIfAbsent(ShopRedisConstants.limitWindowKey(item.getId(), userId),
                    String.valueOf(toEpochMillis(limit.getWindowStartAt())), Duration.ofSeconds(ShopRedisConstants.LIMIT_TTL_SECONDS));
        }
    }

    private void writeOrderState(ShopOrderVO order, Long userId) {
        try {
            String value = objectMapper.createObjectNode().put("userId", userId)
                    .set("order", objectMapper.valueToTree(order)).toString();
            stringRedisTemplate.opsForValue().set(ShopRedisConstants.ORDER_STATE_KEY_PREFIX + order.getOrderNo(), value,
                    Duration.ofSeconds(ShopRedisConstants.ORDER_STATE_TTL_SECONDS));
        } catch (RuntimeException e) {
            log.warn("写入订单状态缓存失败: orderNo={}", order.getOrderNo(), e);
        }
    }

    private void addExpireQueue(ShopOrder order) {
        stringRedisTemplate.opsForZSet().add(ShopRedisConstants.ORDER_EXPIRE_QUEUE, order.getOrderNo(),
                order.getExpireTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    private ShopOrder buildCreatingOrder(String orderNo, String requestId, Long userId, ShopItem item, int quantity,
                                         long pricePoints, long totalPoints, int grantQuantity, LocalDateTime now,
                                         LocalDateTime expireTime) {
        ShopOrder order = new ShopOrder();
        order.setOrderNo(orderNo);
        order.setRequestId(requestId);
        order.setUserId(userId);
        order.setItemId(item.getId());
        order.setItemName(item.getName());
        order.setItemIcon(defaultText(item.getIcon()));
        order.setCosmeticCode(item.getCosmeticCode());
        order.setQuantity(quantity);
        order.setPricePoints(pricePoints);
        order.setTotalPoints(totalPoints);
        order.setGrantQuantity(grantQuantity);
        order.setStatus(ShopConstants.ORDER_CREATING);
        order.setFailReason(ShopConstants.EMPTY_TEXT);
        order.setCreateTime(now);
        order.setPayTime(LocalDateTime.parse(EPOCH_TIME));
        order.setCompleteTime(LocalDateTime.parse(EPOCH_TIME));
        order.setExpireTime(expireTime);
        order.setUpdateTime(now);
        order.setVersion(0);
        return order;
    }

    private String generateOrderNo() {
        return ShopConstants.ORDER_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 28).toUpperCase();
    }

    private int normalizeQuantity(Integer quantity) {
        int value = quantity == null ? 1 : quantity;
        if (value < 1 || value > ShopConstants.ORDER_MAX_QUANTITY) {
            throw new BusinessException("购买数量不合法");
        }
        return value;
    }

    private long multiply(long left, long right, String message) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException e) {
            throw new BusinessException(message);
        }
    }

    private int defaultGrantQuantity(ShopItem item) {
        return item.getGrantQuantity() == null || item.getGrantQuantity() < 1 ? 1 : item.getGrantQuantity();
    }

    private String normalizeRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            throw new BusinessException("请求号不能为空");
        }
        String value = requestId.trim();
        if (value.length() > 80) {
            throw new BusinessException("请求号不能超过80个字符");
        }
        return value;
    }

    private String normalizePolicy(String policy) {
        if (!POLICY_ONCE.equals(policy) && !POLICY_UNLIMITED.equals(policy)
                && !POLICY_COOLDOWN.equals(policy) && !POLICY_WINDOW.equals(policy)) {
            return POLICY_ONCE;
        }
        return policy;
    }

    private long toEpochMillis(LocalDateTime time) {
        return time == null ? 0 : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultText(String value) {
        return value == null ? ShopConstants.EMPTY_TEXT : value;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("用户ID不合法");
        }
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private ShopOrderVO toVO(ShopOrder order) {
        ShopOrderVO vo = new ShopOrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setStatusText(statusText(order.getStatus()));
        return vo;
    }

    private String statusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case ShopConstants.ORDER_FAILED -> "创建失败";
            case ShopConstants.ORDER_CANCELLED -> "已取消";
            case ShopConstants.ORDER_CREATING -> "创建中";
            case ShopConstants.ORDER_PENDING_PAY -> "待支付";
            case ShopConstants.ORDER_PAID -> "权益发放中";
            case ShopConstants.ORDER_COMPLETED -> "已完成";
            default -> "未知";
        };
    }

    private void grantCosmetic(ShopOrder order) {
        GrantCosmeticDTO dto = new GrantCosmeticDTO();
        dto.setUserId(order.getUserId());
        dto.setCosmeticCode(order.getCosmeticCode());
        dto.setQuantity(order.getGrantQuantity());
        dto.setOrderNo(order.getOrderNo());
        dto.setSourceType(ShopConstants.DELIVERY_SOURCE_TYPE);
        Result<CosmeticGrantResultVO> result = userFeignClient.grantCosmetic(dto);
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            throw new BusinessException(result == null ? "装扮发放失败" : defaultText(result.getMessage()));
        }
    }
}

