package com.game.community.model.vo.audit;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ModerationTaskClaimVO implements Serializable {

    private Long taskId;

    private String claimToken;

    private Long handlerId;

    private Integer version;

    private LocalDateTime leaseExpireTime;
}
