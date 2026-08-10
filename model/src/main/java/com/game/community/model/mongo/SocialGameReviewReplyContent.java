package com.game.community.model.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Document(collection = "social_game_review_reply_content")
public class SocialGameReviewReplyContent implements Serializable {

    @Id
    private String id;

    @Indexed(unique = true)
    private String replyId;

    private String reviewId;

    private Long userId;

    private String content;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
