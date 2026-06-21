package com.game.community.search.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.Result;
import com.game.community.model.entity.search.SearchHistory;
import com.game.community.search.service.SearchRecordService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "搜索记录管理")
@RestController
@RequestMapping("/search/record")
@RequiredArgsConstructor
public class SearchRecordController {

    private final SearchRecordService searchRecordService;

    @Operation(summary = "添加搜索记录")
    @PostMapping
    @LoginCheck
    public Result<Void> addRecord(@RequestParam("keyword") String keyword) {
        searchRecordService.addRecord(UserThreadLocal.getUserId(), keyword);
        return Result.success(null);
    }

    @Operation(summary = "获取搜索记录")
    @GetMapping("/list")
    @LoginCheck
    public Result<List<SearchHistory>> getRecords() {
        return Result.success(searchRecordService.getRecords(UserThreadLocal.getUserId()));
    }

    @Operation(summary = "删除单条搜索记录")
    @DeleteMapping("/{id}")
    @LoginCheck
    public Result<Void> deleteRecord(@PathVariable("id") Long id) {
        return searchRecordService.deleteRecord(UserThreadLocal.getUserId(), id)
                ? Result.success(null)
                : Result.error("删除失败，记录不存在");
    }

    @Operation(summary = "清空搜索记录")
    @DeleteMapping("/clear")
    @LoginCheck
    public Result<Long> clearRecords() {
        return Result.success(searchRecordService.clearRecords(UserThreadLocal.getUserId()));
    }
}
