package com.game.community.model.vo.gameaccount;

import lombok.Data;

import java.io.Serializable;

@Data
public class CharacterResourceVO implements Serializable {

    private Long characterId;

    private String characterCode;

    private String name;

    private String title;

    private String icon;

    private Integer rarity;

    private Integer elementType;

    private Integer characterType;

    private Integer status;

    private Boolean owned;
}
