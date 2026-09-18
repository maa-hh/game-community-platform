package com.game.community.model.elasticsearch;

import com.game.community.model.vo.article.ArticleRefVO;
import com.game.community.model.vo.game.GameTagVO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ArticleDocument implements Serializable {

    private Long id;

    private String publicId;

    private Long authorAccountId;

    private String username;

    private String avatar;

    private String title;

    private String summary;

    private String content;

    private String coverUrl;

    private Integer postType;

    private String refArticleId;

    private ArticleRefVO refArticle;

    private String videoUrl;

    private Long categoryId;

    private String categoryName;

    private List<Long> categoryIds;

    private List<String> categoryNames;

    private List<GameTagVO> gameTags;

    private Integer status;

    private LocalDateTime publishedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 标题+摘要+正文摘要+分区+游戏标签的语义向量 */
    private List<Float> embedding;
}
