package com.game.community.model.vo.social;

import com.game.community.model.entity.article.Article;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BrowseHistoryVO implements Serializable {

    private Long articleId;

    private Article article;

    private LocalDateTime browseTime;
}
