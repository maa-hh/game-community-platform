package com.game.community.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.game.community.content.mapper.CategoryMapper;
import com.game.community.content.service.CategoryService;
import com.game.community.model.base.PageResult;
import com.game.community.model.entity.article.Category;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Override
    public PageResult<Category> listEnabledByPage(Integer page, Integer size) {
        Page<Category> result = page(new Page<>(page, size), new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 1)
                .orderByDesc(Category::getSort)
                .orderByDesc(Category::getId));
        return PageResult.of(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public PageResult<Category> listAllByPage(Integer page, Integer size) {
        Page<Category> result = page(new Page<>(page, size), new LambdaQueryWrapper<Category>()
                .orderByDesc(Category::getSort)
                .orderByDesc(Category::getId));
        return PageResult.of(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public List<Category> listEnabled() {
        return list(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 1)
                .orderByDesc(Category::getSort)
                .orderByDesc(Category::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(Category category) {
        if (category.getStatus() == null) {
            category.setStatus(1);
        }
        if (category.getSort() == null) {
            category.setSort(0);
        }
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        return super.save(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Category category) {
        category.setUpdateTime(LocalDateTime.now());
        updateById(category);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        removeById(id);
    }
}
