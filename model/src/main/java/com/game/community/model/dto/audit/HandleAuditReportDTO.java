package com.game.community.model.dto.audit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class HandleAuditReportDTO implements Serializable {

    @NotNull(message = "处理结果不能为空")
    private Integer status;

    @Size(max = 255, message = "处理说明不能超过255字")
    private String handleRemark;
}
