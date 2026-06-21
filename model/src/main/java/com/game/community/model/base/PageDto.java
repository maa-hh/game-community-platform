package com.game.community.model.base;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用分页请求
 */
@Data
public class PageDto implements Serializable {

    private Integer page = 1;

    private Integer size = 10;
}
