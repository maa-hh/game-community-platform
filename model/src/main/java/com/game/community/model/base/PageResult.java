package com.game.community.model.base;

import lombok.Data;

import java.util.List;

/**
 * 通用分页响应
 */
@Data
public class PageResult<T> extends Result<List<T>> {

    private Long page;

    private Long size;

    private Long total;

    public static <T> PageResult<T> of(List<T> records, Long page, Long size, Long total) {
        PageResult<T> result = new PageResult<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(records);
        result.setPage(page);
        result.setSize(size);
        result.setTotal(total);
        return result;
    }
}
