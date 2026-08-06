package com.game.community.content.common.converter;

import com.game.community.model.dto.article.CategoryDTO;
import com.game.community.model.entity.article.Category;
import com.game.community.model.vo.article.CategoryVO;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 分类 Entity ↔ DTO / VO
 */
public final class CategoryConverter {

    private CategoryConverter() {
    }

    public static CategoryVO toVO(Category category) {
        if (category == null) {
            return null;
        }
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setDescription(category.getDescription());
        vo.setStatus(category.getStatus());
        vo.setSort(category.getSort());
        vo.setCreateTime(category.getCreateTime());
        vo.setUpdateTime(category.getUpdateTime());
        return vo;
    }

    public static List<CategoryVO> toVOs(List<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyList();
        }
        return categories.stream().map(CategoryConverter::toVO).filter(Objects::nonNull).toList();
    }

    public static Category toEntity(CategoryDTO dto) {
        Category category = new Category();
        category.setId(dto.getId());
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setStatus(dto.getStatus());
        category.setSort(dto.getSort());
        return category;
    }
}
