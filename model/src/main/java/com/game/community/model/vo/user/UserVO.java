package com.game.community.model.vo.user;

import lombok.Data;

/**
 * 用户详细信息
 */
@Data
public class UserVO {

    private Long id;

    private Long accountId;

    private String username;

    private String avatar;

    /**
     * 当前用户头像待审核时的临时预览地址，仅本人可见。
     */
    private String pendingAvatarUrl;

    private String signature;

    private String phone;

    private Integer status;

    private Integer type;

    private String gameAccount;

    private Integer auditStatus;

    private Integer version;

    private Integer followCount;

    private Integer fansCount;
}
