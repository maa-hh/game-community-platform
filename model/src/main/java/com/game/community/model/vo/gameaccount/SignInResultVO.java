package com.game.community.model.vo.gameaccount;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SignInResultVO implements Serializable {

    private Boolean signed;

    private String yearMonth;

    private Integer dayIndex;

    private Integer signCount;

    private Integer consecutiveDays;

    private String rewardName;

    private Integer rewardType;

    private String rewardCode;

    private Integer quantity;

    private LocalDateTime signTime;
}
