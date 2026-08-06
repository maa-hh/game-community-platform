package com.game.community.model.vo.user;

import lombok.Data;

/**
 * 用户名片（对外公开：列表 / 搜索 / 批量查询）
 */
@Data
public class UserCardVO {

    /** 对外展示账号 ID */
    private Long accountId;

    private String username;

    private String avatar;

    private String signature;
}
