package com.game.community.model.vo.notification;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class NotificationMessageVO implements Serializable {

    private Long id;

    private Integer eventType;

    /** 触发人对外账号 ID */
    private Long actorAccountId;

    private String actorUsername;

    private String actorAvatar;

    private Long articleId;

    private String articlePublicId;

    private Long commentId;

    private Long replyId;

    private Long danmakuId;

    private String videoPublicId;

    private Long reportId;

    /** 跳转目标用户对外账号 ID */
    private Long targetAccountId;

    private String previewText;

    private String resultText;

    private Integer routeType;

    private Integer readStatus;

    private LocalDateTime readTime;

    private LocalDateTime createTime;

    /** 聚合通知：参与用户 */
    private List<NotificationActorVO> aggregateActors;

    /** 聚合通知：总人数 */
    private Integer aggregateTotal;

    private Boolean aggregated;

    private Boolean aggregateHasLike;

    private Boolean aggregateHasFavorite;
}
