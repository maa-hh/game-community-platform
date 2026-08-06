package com.game.community.model.vo.cosmetic;

import lombok.Data;

import java.io.Serializable;

@Data
public class CosmeticGrantResultVO implements Serializable {

    private String cosmeticCode;

    private Integer quantity;

    private Boolean granted;
}
