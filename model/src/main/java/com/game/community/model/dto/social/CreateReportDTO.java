package com.game.community.model.dto.social;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /**
     * 控制器/服务解析后的内部 ID，仅用于落库和内部查询。
     * 文章使用 publicId，用户使用 accountId，评论/回复使用数字 ID。
     */
    @JsonIgnore
    private Long internalTargetId;

    @NotBlank(message = "举报原因不能为空")
    @Size(max = 255, message = "举报原因不能超过255字")
    private String reason;
}
