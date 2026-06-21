package com.game.community.model.vo.gameaccount;

import lombok.Data;

import java.io.Serializable;

@Data
public class ItemResourceVO implements Serializable {

    private Long itemId;

    private String itemCode;

    private String name;

    private Integer itemType;

    private Integer rarity;

    private String icon;

    private Integer maxStackCount;

    private Integer status;

    private Boolean owned;

    private Integer quantity;
}
