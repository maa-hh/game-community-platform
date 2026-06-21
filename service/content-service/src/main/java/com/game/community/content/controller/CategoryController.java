package com.game.community.content.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.content.service.CategoryService;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.entity.article.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 内容分类接口。
 */
@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/list")
    public PageResult<Category> listEnabled(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return categoryService.listEnabledByPage(page, size);
    }

    @AdminCheck
    @GetMapping("/all")
    public PageResult<Category> listAll(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                        @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return categoryService.listAllByPage(page, size);
    }

    @GetMapping("/{id}")
    public Result<Category> getById(@PathVariable("id") Long id) {
        return Result.success(categoryService.getById(id));
    }

    @AdminCheck
    @PostMapping
    public Result<Void> save(@RequestBody Category category) {
        categoryService.save(category);
        return Result.success(null);
    }

    @AdminCheck
    @PutMapping
    public Result<Void> update(@RequestBody Category category) {
        categoryService.update(category);
        return Result.success(null);
    }

    @AdminCheck
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        categoryService.delete(id);
        return Result.success(null);
    }

    @GetMapping("/listEnabled")
    public Result<List<Category>> listEnabled() {
        return Result.success(categoryService.listEnabled());
    }
}
