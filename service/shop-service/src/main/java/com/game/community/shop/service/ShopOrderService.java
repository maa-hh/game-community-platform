package com.game.community.shop.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.shop.CreateShopOrderDTO;
import com.game.community.model.dto.shop.PayShopOrderDTO;
import com.game.community.model.message.ShopOrderCreateMessage;
import com.game.community.model.vo.shop.ShopOrderVO;

public interface ShopOrderService {

    ShopOrderVO createOrder(Long userId, CreateShopOrderDTO dto);

    ShopOrderVO getOrder(Long userId, String orderNo);

    PageResult<ShopOrderVO> pageOrders(Long userId, Long page, Long size);

    void processCreateMessage(ShopOrderCreateMessage message);

    void markCreateFailed(ShopOrderCreateMessage message, String reason);

    void payOrder(Long userId, PayShopOrderDTO dto);

    void cancelOrder(Long userId, String orderNo);

    void cancelExpiredOrders();
}
