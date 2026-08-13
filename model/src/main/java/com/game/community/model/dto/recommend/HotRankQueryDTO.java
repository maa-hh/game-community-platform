package com.game.community.model.dto.recommend;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 热榜查询参数；周期解析和边界归一化由 recommend service 负责。 */
@Data
public class HotRankQueryDTO implements Serializable {

    @Size(max = 16, message = "榜单类型参数过长")
    private String board = "total";

    @Positive(message = "分类 ID 必须为正数")
    private Long categoryId;

    @Size(max = 16, message = "榜单周期参数过长")
    private String periodKey;

    private boolean refresh;
}
