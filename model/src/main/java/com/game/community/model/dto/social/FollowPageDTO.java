package com.game.community.model.dto.social;

import lombok.Data;

import java.io.Serializable;

@Data
public class FollowPageDTO implements Serializable {

    private Long accountId;

    private Long page = 1L;

    private Long size = 20L;
}
