package com.game.community.model.dto.search;

import com.game.community.model.base.PageDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPageDTO extends PageDto implements Serializable {

    private String keyword;

    private Long categoryId;

    /**
     * relevance: ES score, latest: published time desc.
     */
    private String sort = "relevance";

    /** lexical、semantic 或 hybrid；不传时由搜索服务的 AI 配置决定。 */
    private String mode;
}
