package com.game.community.model.vo.social;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class FollowUserVO implements Serializable {

    private Long userId;

    private Long accountId;

    private String username;

    private String avatar;

    private String signature;

    private LocalDateTime createTime;
}
