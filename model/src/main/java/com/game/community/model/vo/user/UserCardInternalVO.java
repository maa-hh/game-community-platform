package com.game.community.model.vo.user;

import lombok.Data;

/**
 * 用户名片（微服务 Feign 内部组装，含内部 userId）
 */
@Data
public class UserCardInternalVO {

    private Long userId;

    private Long accountId;

    private String username;

    private String avatar;

    private String signature;
}
