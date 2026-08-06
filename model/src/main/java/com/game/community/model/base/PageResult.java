package com.game.community.model.base;

import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.model.json.ApiJsonViews;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 通用分页响应
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageResult<T> extends Result<List<T>> {

    @JsonView(ApiJsonViews.Public.class)
    private Long page;

    @JsonView(ApiJsonViews.Public.class)
    private Long size;

    @JsonView(ApiJsonViews.Public.class)
    private Long total;

    /**
     * 分页数据是否仍在后台扩展中（例如 Steam 榜单按需抓取）。
     * 仅由需要异步补齐数据的接口设置，其他分页接口保持 false。
     */
    @JsonView(ApiJsonViews.Public.class)
    private boolean expanding;

    public static <T> PageResult<T> of(List<T> records, Long page, Long size, Long total) {
        return of("查询成功", records, page, size, total);
    }

    public static <T> PageResult<T> of(String message, List<T> records, Long page, Long size, Long total) {
        PageResult<T> result = new PageResult<>();
        result.setCode(200);
        result.setMessage(message == null || message.isBlank() ? "查询成功" : message);
        result.setData(records);
        result.setPage(page);
        result.setSize(size);
        result.setTotal(total);
        return result;
    }
}
