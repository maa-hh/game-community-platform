package com.game.community.model.dto.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 分类创建/更新 DTO
 */
@Data
public class CategoryDTO implements Serializable {

    private Long id;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 64, message = "分类名称不能超过64字")
    private String name;

    @Size(max = 255, message = "分类描述不能超过255字")
    private String description;

    /** 0-禁用 1-启用 */
    private Integer status;

    private Integer sort;
}
