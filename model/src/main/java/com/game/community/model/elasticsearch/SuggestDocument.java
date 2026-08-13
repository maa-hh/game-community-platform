package com.game.community.model.elasticsearch;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class SuggestDocument implements Serializable {

    private Long id;

    private String suggest;

    private String suggestNgram;

    /** 对应 t_suggest_term.id */
    private Long termId;

    private Integer weight;

    private String sourceType;

    /** 同一个词可能同时来自文章和游戏，供 sourceType 过滤使用。 */
    private List<String> sourceTypes;

    private Long sourceArticleId;
}
