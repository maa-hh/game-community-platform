package com.game.community.model.dto.cosmetic;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class SaveCosmeticDefDTO implements Serializable {

    private Long id;

    @NotBlank(message = "装扮编码不能为空")
    private String code;

    @NotBlank(message = "名称不能为空")
    private String name;

    @NotBlank(message = "分类不能为空")
    private String category;

    @NotBlank(message = "效果类型不能为空")
    private String effectMode;

    private String slot;

    private String previewUrl;

    @NotBlank(message = "样式资源不能为空")
    private String assetJson;

    private String consumableConfig;

    private Integer defaultDurationSeconds;

    private Integer status;
}
