package com.game.community.model.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论正文。大文本放 MongoDB，结构化信息放 MySQL。
 */
@Data
@Document(collection = "social_comment_content")
public class SocialCommentContent implements Serializable {

    @Id
    private String id;

    @Indexed(unique = true)
    private Long commentId;

    private Long articleId;

    private Long userId;

    private String content;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
