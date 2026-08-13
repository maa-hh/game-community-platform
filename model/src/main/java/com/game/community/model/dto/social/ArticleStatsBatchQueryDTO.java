package com.game.community.model.dto.social;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量查询文章统计的公开请求参数。
 *
 * <p>请求只接收文章 publicId，避免把内容库内部主键带到 HTTP 边界。</p>
 */
@Data
public class ArticleStatsBatchQueryDTO implements Serializable {

    @NotEmpty(message = "文章ID不能为空")
    private List<String> articleIds;
}
