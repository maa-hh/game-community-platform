package com.game.community.shop.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.shop.CreateShopOrderDTO;
import com.game.community.model.dto.shop.ExchangeShopItemDTO;
import com.game.community.model.dto.shop.PayShopOrderDTO;
import com.game.community.model.entity.shop.ShopDeliveryTask;
import com.game.community.model.vo.shop.ExchangeShopResultVO;
import com.game.community.model.vo.shop.ShopOrderVO;

public interface ShopOrderService {

    ShopOrderVO createOrder(Long userId, CreateShopOrderDTO dto);

    ExchangeShopResultVO exchange(Long userId, ExchangeShopItemDTO dto);

    ShopOrderVO getOrder(Long userId, String orderNo);

    PageResult<ShopOrderVO> pageOrders(Long userId, Long page, Long size);

    void processCreatingOrder(String orderNo);

    void markCreatingFailed(String orderNo, String reason);

    void processDeliveryTask(ShopDeliveryTask task, String token);

    void markDeliveryFailed(ShopDeliveryTask task, String token, String reason);

    void payOrder(Long userId, PayShopOrderDTO dto);

    void cancelOrder(Long userId, String orderNo);

    void cancelExpiredOrders();
}
