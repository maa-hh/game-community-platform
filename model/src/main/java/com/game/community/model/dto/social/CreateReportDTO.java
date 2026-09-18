package com.game.community.model.dto.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class CreateReportDTO implements Serializable {

    @NotNull(message = "举报目标类型不能为空")
    private Integer targetType;

    @NotBlank(message = "举报目标ID不能为空")
    private String targetId;

    @NotBlank(message = "举报原因不能为空")
    @Size(max = 255, message = "举报原因不能超过255字")
    private String reason;
}
