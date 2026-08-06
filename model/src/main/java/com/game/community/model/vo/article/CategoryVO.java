package com.game.community.model.vo.article;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分类 VO（对外 API）
 */
@Data
public class CategoryVO implements Serializable {

    private Long id;
    private String name;
    private String description;
    private Integer status;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
