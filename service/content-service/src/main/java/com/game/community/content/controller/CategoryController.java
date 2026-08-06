package com.game.community.content.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.content.service.CategoryService;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.article.CategoryDTO;
import com.game.community.model.vo.article.CategoryVO;
import jakarta.validation.Valid;
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
    public PageResult<CategoryVO> listEnabled(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                              @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return categoryService.listEnabledByPageApi(page, size);
    }

    @AdminCheck
    @GetMapping("/all")
    public PageResult<CategoryVO> listAll(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                          @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return categoryService.listAllByPageApi(page, size);
    }

    @GetMapping("/{id}")
    public Result<CategoryVO> getById(@PathVariable("id") Long id) {
        return categoryService.getCategoryByIdApi(id);
    }

    @AdminCheck
    @PostMapping
    public Result<Void> save(@Valid @RequestBody CategoryDTO dto) {
        return categoryService.saveCategory(dto);
    }

    @AdminCheck
    @PutMapping
    public Result<Void> update(@Valid @RequestBody CategoryDTO dto) {
        return categoryService.updateCategory(dto);
    }

    @AdminCheck
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        return categoryService.deleteCategory(id);
    }

    @GetMapping("/listEnabled")
    public Result<List<CategoryVO>> listEnabled() {
        return categoryService.listEnabledApi();
    }
}
