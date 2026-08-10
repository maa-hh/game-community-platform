package com.game.community.model.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 游戏短评正文，Steam-service 负责评分，Social-service 负责正文和互动。 */
@Data
@Document(collection = "social_game_review_content")
public class SocialGameReviewContent implements Serializable {

    @Id
    private String id;

    @Indexed(unique = true)
    private String reviewId;

    private Long appId;

    private Long userId;

    private String content;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
