package com.game.community.model.vo.gameaccount;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class OwnedItemVO implements Serializable {

    private Long itemId;

    private String itemCode;

    private String name;

    private String icon;

    private Integer itemType;

    private Integer rarity;

    private Integer quantity;

    private Integer status;

    private LocalDateTime lastObtainTime;
}
