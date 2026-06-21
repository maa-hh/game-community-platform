package com.game.community.model.vo.gameaccount;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class GameAccountBindVO implements Serializable {

    private Long bindId;

    private Long gameAccountId;

    private String accountNo;

    private String name;

    private LocalDateTime bindTime;

    private LocalDateTime createTime;
}
