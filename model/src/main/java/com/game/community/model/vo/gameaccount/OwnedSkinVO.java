package com.game.community.model.vo.gameaccount;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class OwnedSkinVO implements Serializable {

    private Long skinId;

    private String skinCode;

    private Long characterId;

    private String characterCode;

    private String name;

    private String icon;

    private Integer rarity;

    private Integer equipStatus;

    private Integer status;

    private LocalDateTime obtainTime;
}
