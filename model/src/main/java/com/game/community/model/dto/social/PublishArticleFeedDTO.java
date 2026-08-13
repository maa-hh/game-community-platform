package com.game.community.model.dto.social;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 内容服务向社交服务投递文章 Feed 的内部命令。 */
@Data
public class PublishArticleFeedDTO implements Serializable {

    private Long authorId;

    private Long articleId;

    private LocalDateTime publishedTime;
}
