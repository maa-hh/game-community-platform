package com.game.community.model.vo.article;

import com.game.community.model.vo.game.GameTagVO;
import com.game.community.model.json.ApiJsonViews;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文章详情VO（MySQL + MongoDB合并）
 */
@Data
public class ArticleDetailVO implements Serializable {

    @JsonView(ApiJsonViews.Internal.class)
    private Long id;
    /** 对外公开帖子 ID。 */
    @JsonView(ApiJsonViews.Public.class)
    private String publicId;
    /** 作者对外账号 ID */
    @JsonView(ApiJsonViews.Public.class)
    private Long authorAccountId;
    /** 作者昵称（公开详情填充） */
    @JsonView(ApiJsonViews.Public.class)
    private String username;
    /** 作者头像 */
    @JsonView(ApiJsonViews.Public.class)
    private String avatar;
    @JsonView(ApiJsonViews.Public.class)
    private String title;
    @JsonView(ApiJsonViews.Public.class)
    private String summary;
    @JsonView(ApiJsonViews.Public.class)
    private String coverUrl;
    @JsonView(ApiJsonViews.Public.class)
    private Integer postType;
    /** 转发引用的原帖 ID */
    @JsonView(ApiJsonViews.Public.class)
    private String refArticleId;
    /** 原帖简要信息（转发帖详情用） */
    @JsonView(ApiJsonViews.Public.class)
    private ArticleRefVO refArticle;
    @JsonView(ApiJsonViews.Public.class)
    private String videoUrl;
    @JsonView(ApiJsonViews.Public.class)
    private Long categoryId;
    @JsonView(ApiJsonViews.Public.class)
    private List<Long> categoryIds;
    @JsonView(ApiJsonViews.Public.class)
    private List<String> categoryNames;
    @JsonView(ApiJsonViews.Public.class)
    private Integer status;
    @JsonView(ApiJsonViews.Public.class)
    private String auditMessage;
    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime scheduledPublishTime;
    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime publishedTime;
    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime createTime;
    @JsonView(ApiJsonViews.Public.class)
    private LocalDateTime updateTime;

    // MongoDB 内容
    @JsonView(ApiJsonViews.Public.class)
    private String content;
    @JsonView(ApiJsonViews.Public.class)
    private String contentHtml;
    @JsonView(ApiJsonViews.Public.class)
    private Map<String, String> contentParagraphs;
    @JsonView(ApiJsonViews.Public.class)
    private List<String> imageUrls;

    /** 关联游戏标签 */
    @JsonView(ApiJsonViews.Public.class)
    private List<GameTagVO> gameTags;
}
