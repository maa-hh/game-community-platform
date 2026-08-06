package com.game.community.model.vo.article;

import com.game.community.model.vo.game.GameTagVO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ArticleSearchItemVO implements Serializable {

    private Long id;

    private String publicId;

    private Long authorAccountId;

    private String username;

    private String avatar;

    private String title;

    private String summary;

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

    private LocalDateTime publishedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
