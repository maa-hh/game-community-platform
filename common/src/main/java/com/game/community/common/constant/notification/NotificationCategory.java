package com.game.community.common.constant.notification;

import java.util.List;

/**
 * 通知中心分类（前端 Tab）
 */
public final class NotificationCategory {

    public static final String SYSTEM = "system";
    public static final String LIKE_FAVORITE = "like_favorite";
    public static final String FOLLOW = "follow";
    public static final String COMMENT = "comment";

    private NotificationCategory() {
    }

    /** 返回通知中心固定分类顺序，供摘要接口和前端 Tab 共用。 */
    public static List<String> all() {
        return List.of(SYSTEM, LIKE_FAVORITE, FOLLOW, COMMENT);
    }

    public static List<Integer> eventTypesOf(String category) {
        if (category == null) {
            return List.of();
        }
        return switch (category) {
            case SYSTEM -> List.of(
                    NotificationConstants.EventType.REPORT_SUBMITTED,
                    NotificationConstants.EventType.REPORT_RESULT,
                    NotificationConstants.EventType.PENALTY_RESULT,
                    NotificationConstants.EventType.PROFILE_AUDIT_PASSED,
                    NotificationConstants.EventType.PROFILE_AUDIT_REJECTED,
                    NotificationConstants.EventType.PROFILE_AUDIT_HUMAN_REVIEW,
                    NotificationConstants.EventType.ARTICLE_AUDIT_REJECTED,
                    NotificationConstants.EventType.ARTICLE_AUDIT_PASSED,
                    NotificationConstants.EventType.ARTICLE_AUDIT_HUMAN_REVIEW
            );
            case LIKE_FAVORITE -> List.of(
                    NotificationConstants.EventType.ARTICLE_LIKE,
                    NotificationConstants.EventType.COMMENT_LIKE,
                    NotificationConstants.EventType.REPLY_LIKE,
                    NotificationConstants.EventType.ARTICLE_FAVORITE,
                    NotificationConstants.EventType.GAME_REVIEW_LIKE,
                    NotificationConstants.EventType.GAME_REVIEW_REPLY_LIKE
            );
            case FOLLOW -> List.of(NotificationConstants.EventType.FOLLOW);
            case COMMENT -> List.of(
                    NotificationConstants.EventType.ARTICLE_COMMENT,
                    NotificationConstants.EventType.COMMENT_REPLY,
                    NotificationConstants.EventType.DANMAKU_COMMENT,
                    NotificationConstants.EventType.GAME_REVIEW_REPLY
            );
            default -> List.of();
        };
    }

    public static boolean isAggregatable(Integer eventType) {
        if (eventType == null) {
            return false;
        }
        return eventType == NotificationConstants.EventType.ARTICLE_LIKE
                || eventType == NotificationConstants.EventType.COMMENT_LIKE
                || eventType == NotificationConstants.EventType.REPLY_LIKE
                || eventType == NotificationConstants.EventType.ARTICLE_FAVORITE;
    }
}
