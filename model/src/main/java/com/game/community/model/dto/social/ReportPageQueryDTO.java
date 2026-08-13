package com.game.community.model.dto.social;

import lombok.Data;

import java.io.Serializable;

/** 举报后台分页筛选参数，统一 Controller 的查询边界。 */
@Data
public class ReportPageQueryDTO implements Serializable {

    private Long page = 1L;

    private Long size = 20L;

    private Integer status;

    private Integer targetType;
}
