package com.game.community.shop.job;

import com.game.community.shop.service.ShopOrderService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 低频修复任务不参与请求主链路，避免高并发下用扫描替代原子扣减。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopConsistencyJobHandler {

    private final ShopOrderService orderService;

    /** 批量取消已过期的待支付订单并释放数据库/Redis 预占。 */
    @XxlJob("shopExpireOrdersJob")
    public void expireOrders() {
        orderService.cancelExpiredOrders();
    }

    /** 以 MySQL 为准重建 Redis 热点库存和限购缓存。 */
    @XxlJob("shopRedisReconcileJob")
    public void reconcileRedis() {
        orderService.reconcileRedisState();
        log.info("商城 Redis 库存与限购缓存对账完成");
    }
}
