package com.game.community.model.vo.audit;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ModerationTaskClaimVO implements Serializable {

    /** 对外工单标识，不是数据库主键。 */
    private String taskKey;

    private String claimToken;

    private Long handlerAccountId;

    private Integer version;

    private LocalDateTime leaseExpireTime;
}
