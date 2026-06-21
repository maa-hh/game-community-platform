package com.game.community.model.vo.article;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ArticleSearchItemVO implements Serializable {

    private Long id;

    private Long userId;

    private String username;

    private String avatar;

    private String title;

    private String summary;

    private String coverUrl;

    private Long categoryId;

    private String categoryName;

    private LocalDateTime publishedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
