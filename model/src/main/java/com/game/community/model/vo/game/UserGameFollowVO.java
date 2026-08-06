package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserGameFollowVO implements Serializable {

    private Long appId;

    private String name;

    private String coverUrl;

    private BigDecimal avgScore;

    private Integer reviewCount;

    private Integer discussCount;

    /** manual / steam_import / discover */
    private String source;

    /** 是否同时在 Steam 库中 */
    private Boolean steamOwned;

    private Integer playtimeForever;

    private LocalDateTime followTime;
}
