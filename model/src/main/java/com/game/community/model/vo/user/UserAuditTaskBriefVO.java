package com.game.community.model.vo.user;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserAuditTaskBriefVO implements Serializable {

    private Long id;

    private Long userId;

    private String fieldType;

    private String status;

    private LocalDateTime updateTime;
}
