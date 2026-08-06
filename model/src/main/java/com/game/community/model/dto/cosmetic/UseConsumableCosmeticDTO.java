package com.game.community.model.dto.cosmetic;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class UseConsumableCosmeticDTO implements Serializable {

    @NotBlank(message = "装扮编码不能为空")
    private String code;
}
