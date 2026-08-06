package com.game.community.model.vo.cosmetic;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ActiveEffectVO implements Serializable {

    private String effectCode;

    private String sourceCosmeticCode;

    private LocalDateTime expireAt;

    private String payloadJson;
}
