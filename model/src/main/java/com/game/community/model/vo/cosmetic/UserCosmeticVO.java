package com.game.community.model.vo.cosmetic;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserCosmeticVO implements Serializable {

    private String cosmeticCode;

    private String name;

    private String category;

    private String effectMode;

    private String slot;

    private String previewUrl;

    private String assetJson;

    private Integer quantity;

    private Boolean equipped;

    private Boolean canUse;

    private String state;

    private LocalDateTime acquiredAt;

    private LocalDateTime expireAt;
}
