package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.user.UserNotificationOutbox;

/** 仅记录 Kafka 通知最终投递失败，不负责扫描或重试。 */
public interface UserNotificationOutboxMapper extends BaseMapper<UserNotificationOutbox> {
}
