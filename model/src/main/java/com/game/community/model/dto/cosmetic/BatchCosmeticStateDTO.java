package com.game.community.model.dto.cosmetic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 批量查询用户装扮状态，供商城商品页避免逐商品远程调用。 */
@Data
public class BatchCosmeticStateDTO implements Serializable {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotEmpty(message = "装扮编码列表不能为空")
    @Size(max = 100, message = "单次最多查询100个装扮")
    private List<@NotBlank(message = "装扮编码不能为空") String> cosmeticCodes;
}
