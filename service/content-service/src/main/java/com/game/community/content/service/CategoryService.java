package com.game.community.content.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.game.community.model.base.PageResult;
import com.game.community.model.entity.article.Category;

import java.util.List;

public interface CategoryService extends IService<Category> {

    PageResult<Category> listEnabledByPage(Integer page, Integer size);

    PageResult<Category> listAllByPage(Integer page, Integer size);

    List<Category> listEnabled();

    void update(Category category);

    void delete(Long id);
}
