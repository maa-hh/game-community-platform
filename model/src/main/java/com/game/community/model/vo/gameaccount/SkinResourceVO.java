package com.game.community.model.vo.gameaccount;

import lombok.Data;

import java.io.Serializable;

@Data
public class SkinResourceVO implements Serializable {

    private Long skinId;

    private String skinCode;

    private Long characterId;

    private String characterCode;

    private String name;

    private String icon;

    private Integer rarity;

    private Integer status;

    private Boolean owned;

    private Integer equipStatus;
}
