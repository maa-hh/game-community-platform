package com.game.community.model.dto.cosmetic;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class UnequipCosmeticDTO implements Serializable {

    @NotBlank(message = "槽位不能为空")
    private String slot;
}
