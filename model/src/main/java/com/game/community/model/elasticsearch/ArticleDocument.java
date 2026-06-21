package com.game.community.model.elasticsearch;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class ArticleDocument implements Serializable {

    private Long id;

    private Long userId;

    private String username;

    private String avatar;

    private String title;

    private String summary;

    private String content;

    private String coverUrl;

    private Long categoryId;

    private String categoryName;

    private Integer status;

    private LocalDateTime publishedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
