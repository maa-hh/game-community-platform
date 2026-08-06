package com.game.community.model.message;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArticleBehaviorMessage implements Serializable {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    /** Outbox 生成的全局幂等事件 ID。 */
    private String eventId;

    /** 消息结构版本，便于后续演进。 */
    private Integer schemaVersion = CURRENT_SCHEMA_VERSION;

    private Long articleId;

    /** 兼容旧版 Kafka 消息字段 likeCount */
    @JsonAlias("likeCount")
    private Long likeDelta;

    /** 兼容旧版 Kafka 消息字段 commentCount */
    @JsonAlias("commentCount")
    private Long commentDelta;

    /** 兼容旧版 Kafka 消息字段 viewCount */
    @JsonAlias("viewCount")
    private Long viewDelta;

    @JsonAlias("favoriteCount")
    private Long favoriteDelta;

    @JsonAlias("shareCount")
    private Long shareDelta;

    @JsonAlias("commentLikeCount")
    private Long commentLikeDelta;

    @JsonAlias("replyLikeCount")
    private Long replyLikeDelta;

    /** 事件时间（毫秒），Streams 按日分组用 */
    private Long eventTimeMs;

    public ArticleBehaviorMessage(Long articleId, Long likeDelta, Long commentDelta, Long viewDelta) {
        this(articleId, likeDelta, commentDelta, viewDelta, 0L, 0L, 0L, 0L, System.currentTimeMillis());
    }

    public ArticleBehaviorMessage(Long articleId,
                                  Long likeDelta,
                                  Long commentDelta,
                                  Long viewDelta,
                                  Long favoriteDelta,
                                  Long shareDelta) {
        this(articleId, likeDelta, commentDelta, viewDelta, favoriteDelta, shareDelta, 0L, 0L, System.currentTimeMillis());
    }

    public ArticleBehaviorMessage(Long articleId,
                                  Long likeDelta,
                                  Long commentDelta,
                                  Long viewDelta,
                                  Long favoriteDelta,
                                  Long shareDelta,
                                  Long eventTimeMs) {
        this(articleId, likeDelta, commentDelta, viewDelta, favoriteDelta, shareDelta, 0L, 0L, eventTimeMs);
    }

    public ArticleBehaviorMessage(Long articleId,
                                  Long likeDelta,
                                  Long commentDelta,
                                  Long viewDelta,
                                  Long favoriteDelta,
                                  Long shareDelta,
                                  Long commentLikeDelta,
                                  Long replyLikeDelta,
                                  Long eventTimeMs) {
        this.articleId = articleId;
        this.likeDelta = likeDelta;
        this.commentDelta = commentDelta;
        this.viewDelta = viewDelta;
        this.favoriteDelta = favoriteDelta;
        this.shareDelta = shareDelta;
        this.commentLikeDelta = commentLikeDelta;
        this.replyLikeDelta = replyLikeDelta;
        this.eventTimeMs = eventTimeMs == null ? System.currentTimeMillis() : eventTimeMs;
    }
}
