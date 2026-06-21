package com.game.community.model.vo.gameaccount;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SignInStatusVO implements Serializable {

    private String yearMonth;

    private Boolean signedToday;

    private Integer signCount;

    private Integer consecutiveDays;

    private Long signBits;

    private LocalDateTime lastSignInDate;

    private List<Integer> signedDays;
}
