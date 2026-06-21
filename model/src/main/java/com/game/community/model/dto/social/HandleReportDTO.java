package com.game.community.model.dto.social;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class HandleReportDTO implements Serializable {

    @NotNull(message = "处理状态不能为空")
    private Integer status;

    @Size(max = 255, message = "处理说明不能超过255字")
    private String handleRemark;
}
