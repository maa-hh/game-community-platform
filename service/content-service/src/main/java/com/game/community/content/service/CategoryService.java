package com.game.community.content.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.article.CategoryDTO;
import com.game.community.model.entity.article.Category;
import com.game.community.model.vo.article.CategoryVO;

import java.util.List;

public interface CategoryService extends IService<Category> {

    PageResult<Category> listEnabledByPage(Integer page, Integer size);

    PageResult<Category> listAllByPage(Integer page, Integer size);

    List<Category> listEnabled();

    void update(Category category);

    void delete(Long id);

    // HTTP API
    PageResult<CategoryVO> listEnabledByPageApi(Integer page, Integer size);

    PageResult<CategoryVO> listAllByPageApi(Integer page, Integer size);

    Result<CategoryVO> getCategoryByIdApi(Long id);

    Result<Void> saveCategory(CategoryDTO dto);

    Result<Void> updateCategory(CategoryDTO dto);

    Result<Void> deleteCategory(Long id);

    Result<List<CategoryVO>> listEnabledApi();
}
