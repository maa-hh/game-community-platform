package com.game.community.model.message;

import com.game.community.model.dto.article.ArticleDTO;
import lombok.Data;

import java.util.List;

/** 内容服务等待 AI 结果时保存的发布快照。 */
@Data
public class ArticleModerationContext {
    private String consumer = AiTaskRequestMessage.CONSUMER_CONTENT;
    private Long articleId;
    private Long userId;
    private ArticleDTO article;
    private String coverUrl;
    private List<String> imageUrls;
}
