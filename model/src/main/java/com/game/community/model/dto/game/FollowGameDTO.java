package com.game.community.model.dto.game;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class FollowGameDTO implements Serializable {

    @NotNull(message = "游戏 ID 不能为空")
    private Long appId;

    /** manual / discover / steam_import */
    private String source;
}
