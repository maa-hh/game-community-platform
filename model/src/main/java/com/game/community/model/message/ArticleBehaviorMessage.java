package com.game.community.model.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleBehaviorMessage implements Serializable {

    private Long articleId;

    private Long likeCount;

    private Long commentCount;

    private Long viewCount;
}
