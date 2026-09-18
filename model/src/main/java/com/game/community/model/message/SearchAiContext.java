package com.game.community.model.message;

import com.game.community.model.elasticsearch.ArticleDocument;
import lombok.Data;

/** 搜索 AI 任务的异步回写上下文。 */
@Data
public class SearchAiContext {
    public static final String ARTICLE_EMBEDDING = "ARTICLE_EMBEDDING";
    public static final String QUERY_EMBEDDING = "QUERY_EMBEDDING";
    public static final String SEARCH_TERMS = "SEARCH_TERMS";

    private String consumer = AiTaskRequestMessage.CONSUMER_SEARCH;
    private String operation;
    private String query;
    private ArticleDocument document;
}
