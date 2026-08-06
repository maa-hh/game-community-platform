package com.game.community.model.vo.cosmetic;

import lombok.Data;

import java.io.Serializable;

@Data
public class CosmeticItemStateVO implements Serializable {

    private Boolean owned;

    private Boolean equipped;
}
