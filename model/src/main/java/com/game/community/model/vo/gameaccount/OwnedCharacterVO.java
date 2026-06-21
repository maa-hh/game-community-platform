package com.game.community.model.vo.gameaccount;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class OwnedCharacterVO implements Serializable {

    private Long characterId;

    private String characterCode;

    private String name;

    private String icon;

    private Integer rarity;

    private Integer level;

    private Integer status;

    private LocalDateTime obtainTime;
}
