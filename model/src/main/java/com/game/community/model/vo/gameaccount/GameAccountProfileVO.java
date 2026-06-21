package com.game.community.model.vo.gameaccount;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class GameAccountProfileVO implements Serializable {

    private Long gameAccountId;

    private Boolean bound;

    private String accountNo;

    private String name;

    private Integer level;

    private Long gold;

    private Long diamond;

    private String currentSeasonRank;

    private String historySeasonRank;

    private Integer status;

    private LocalDateTime updateTime;
}
