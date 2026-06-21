package com.game.community.game.service;

import com.game.community.model.message.ShopOrderPaidMessage;

public interface GameDeliveryService {

    void deliver(ShopOrderPaidMessage message);
}
