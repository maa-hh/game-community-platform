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
}
