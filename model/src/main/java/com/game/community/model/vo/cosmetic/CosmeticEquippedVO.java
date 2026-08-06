package com.game.community.model.vo.cosmetic;

import lombok.Data;

import java.io.Serializable;

@Data
public class CosmeticEquippedVO implements Serializable {

    private String code;

    private String name;

    private String category;

    private String assetJson;
}
