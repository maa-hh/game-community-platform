package com.game.community.content.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.game.community.content.common.converter.CategoryConverter;
import com.game.community.content.mapper.CategoryMapper;
import com.game.community.content.service.CategoryService;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.article.CategoryDTO;
import com.game.community.model.entity.article.Category;
import com.game.community.model.vo.article.CategoryVO;
import com.game.community.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private static final String ENABLED_CATEGORY_CACHE_KEY = "content:category:enabled";

    private final RedisUtils redisUtils;

    public CategoryServiceImpl(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

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
        try {
            String cached = redisUtils.get(ENABLED_CATEGORY_CACHE_KEY);
            if (cached != null && !cached.isBlank()) {
                return JSON.parseArray(cached, Category.class);
            }
        } catch (Exception e) {
            log.warn("读取分类缓存失败，将回源数据库", e);
        }
        List<Category> categories = list(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 1)
                .orderByDesc(Category::getSort)
                .orderByDesc(Category::getId));
        try {
            redisUtils.set(ENABLED_CATEGORY_CACHE_KEY, JSON.toJSONString(categories), 300, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入分类缓存失败", e);
        }
        return categories;
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
        boolean saved = super.save(category);
        evictEnabledCache();
        return saved;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Category category) {
        category.setUpdateTime(LocalDateTime.now());
        updateById(category);
        evictEnabledCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        removeById(id);
        evictEnabledCache();
    }

    @Override
    public PageResult<CategoryVO> listEnabledByPageApi(Integer page, Integer size) {
        PageResult<Category> raw = listEnabledByPage(page, size);
        return PageResult.of(CategoryConverter.toVOs(raw.getData()), raw.getPage(), raw.getSize(), raw.getTotal());
    }

    @Override
    public PageResult<CategoryVO> listAllByPageApi(Integer page, Integer size) {
        PageResult<Category> raw = listAllByPage(page, size);
        return PageResult.of(CategoryConverter.toVOs(raw.getData()), raw.getPage(), raw.getSize(), raw.getTotal());
    }

    @Override
    public Result<CategoryVO> getCategoryByIdApi(Long id) {
        return Result.success(CategoryConverter.toVO(getById(id)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> saveCategory(CategoryDTO dto) {
        save(CategoryConverter.toEntity(dto));
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateCategory(CategoryDTO dto) {
        update(CategoryConverter.toEntity(dto));
        return Result.success(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> deleteCategory(Long id) {
        delete(id);
        return Result.success(null);
    }

    @Override
    public Result<List<CategoryVO>> listEnabledApi() {
        return Result.success(CategoryConverter.toVOs(listEnabled()));
    }

    private void evictEnabledCache() {
        try {
            redisUtils.del(ENABLED_CATEGORY_CACHE_KEY);
        } catch (Exception e) {
            log.warn("清理分类缓存失败", e);
        }
    }
}
