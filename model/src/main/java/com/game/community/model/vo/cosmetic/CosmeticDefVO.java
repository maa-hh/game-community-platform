package com.game.community.model.vo.cosmetic;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class CosmeticDefVO implements Serializable {

    private Long id;

    private String code;

    private String name;

    private String category;

    private String effectMode;

    private String slot;

    private String previewUrl;

    private String assetJson;

    private String consumableConfig;

    private Integer defaultDurationSeconds;

    private Integer status;
}
