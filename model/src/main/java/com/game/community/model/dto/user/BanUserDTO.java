package com.game.community.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 封禁用户请求（仅管理员）
 */
@Data
public class BanUserDTO {

    @NotBlank(message = "封禁原因不能为空")
    @Size(max = 255, message = "封禁原因不能超过255个字符")
    private String reason;

    /** 封禁时长（小时），null或0表示永久封禁 */
    private Integer durationHours;
}
