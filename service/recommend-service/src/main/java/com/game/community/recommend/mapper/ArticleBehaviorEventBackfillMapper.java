package com.game.community.recommend.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ArticleBehaviorEventBackfillMapper {

    @Select("SELECT COUNT(*) FROM t_article_behavior_event")
    long countAll();

    @Insert("""
            INSERT INTO t_article_behavior_event (
                event_id, article_id, like_delta, comment_delta, view_delta, favorite_delta, share_delta,
                score_delta, event_time, event_time_ms
            )
            SELECT
                CONCAT('backfill-like-', l.id), l.article_id,
                1, 0, 0, 0, 0,
                2.0,
                COALESCE(l.create_time, #{fallbackTime}),
                UNIX_TIMESTAMP(COALESCE(l.create_time, #{fallbackTime})) * 1000
            FROM t_social_article_like l
            INNER JOIN t_article a ON a.id = l.article_id AND a.status = 1 AND a.deleted = 0
            """)
    int backfillLikes(@Param("fallbackTime") String fallbackTime);

    @Insert("""
            INSERT INTO t_article_behavior_event (
                event_id, article_id, like_delta, comment_delta, view_delta, favorite_delta, share_delta,
                score_delta, event_time, event_time_ms
            )
            SELECT
                CONCAT('backfill-favorite-', f.id), f.article_id,
                0, 0, 0, 1, 0,
                2.0,
                COALESCE(f.create_time, #{fallbackTime}),
                UNIX_TIMESTAMP(COALESCE(f.create_time, #{fallbackTime})) * 1000
            FROM t_social_favorite f
            INNER JOIN t_article a ON a.id = f.article_id AND a.status = 1 AND a.deleted = 0
            """)
    int backfillFavorites(@Param("fallbackTime") String fallbackTime);

    @Insert("""
            INSERT INTO t_article_behavior_event (
                event_id, article_id, like_delta, comment_delta, view_delta, favorite_delta, share_delta,
                score_delta, event_time, event_time_ms
            )
            SELECT
                CONCAT('backfill-comment-', c.id), c.article_id,
                0, 1, 0, 0, 0,
                5.0,
                COALESCE(c.create_time, #{fallbackTime}),
                UNIX_TIMESTAMP(COALESCE(c.create_time, #{fallbackTime})) * 1000
            FROM t_social_comment c
            INNER JOIN t_article a ON a.id = c.article_id AND a.status = 1 AND a.deleted = 0
            WHERE c.status = 1 AND c.deleted = 0
            """)
    int backfillComments(@Param("fallbackTime") String fallbackTime);

    @Insert("""
            INSERT INTO t_article_behavior_event (
                event_id, article_id, like_delta, comment_delta, view_delta, favorite_delta, share_delta,
                score_delta, event_time, event_time_ms
            )
            SELECT
                CONCAT('backfill-reply-', r.id), r.article_id,
                0, 1, 0, 0, 0,
                5.0,
                COALESCE(r.create_time, #{fallbackTime}),
                UNIX_TIMESTAMP(COALESCE(r.create_time, #{fallbackTime})) * 1000
            FROM t_social_reply r
            INNER JOIN t_article a ON a.id = r.article_id AND a.status = 1 AND a.deleted = 0
            WHERE r.status = 1 AND r.deleted = 0
            """)
    int backfillReplies(@Param("fallbackTime") String fallbackTime);

    @Insert("""
            INSERT INTO t_article_behavior_event (
                event_id, article_id, like_delta, comment_delta, view_delta, favorite_delta, share_delta,
                score_delta, event_time, event_time_ms
            )
            SELECT
                CONCAT('backfill-view-', b.id), b.article_id,
                0, 0, 1, 0, 0,
                1.0,
                COALESCE(b.create_time, #{fallbackTime}),
                UNIX_TIMESTAMP(COALESCE(b.create_time, #{fallbackTime})) * 1000
            FROM t_social_browse_history b
            INNER JOIN t_article a ON a.id = b.article_id AND a.status = 1 AND a.deleted = 0
            """)
    int backfillViews(@Param("fallbackTime") String fallbackTime);

    @Insert("""
            INSERT INTO t_article_behavior_event (
                event_id, article_id, like_delta, comment_delta, view_delta, favorite_delta, share_delta,
                score_delta, event_time, event_time_ms
            )
            SELECT
                CONCAT('backfill-repost-', repost.id), repost.ref_article_id,
                0, 0, 0, 0, 1,
                3.0,
                COALESCE(repost.published_time, repost.create_time, #{fallbackTime}),
                UNIX_TIMESTAMP(COALESCE(repost.published_time, repost.create_time, #{fallbackTime})) * 1000
            FROM t_article repost
            INNER JOIN t_article orig ON orig.id = repost.ref_article_id AND orig.status = 1 AND orig.deleted = 0
            WHERE repost.post_type = 4
              AND repost.status = 1
              AND repost.deleted = 0
              AND repost.ref_article_id IS NOT NULL
            """)
    int backfillReposts(@Param("fallbackTime") String fallbackTime);

    @Insert("""
            INSERT INTO t_article_behavior_event (
                event_id, article_id, like_delta, comment_delta, view_delta, favorite_delta, share_delta,
                score_delta, event_time, event_time_ms
            )
            SELECT
                CONCAT('backfill-share-stats-', s.id), s.article_id,
                0, 0, 0, 0, s.share_count,
                s.share_count * 3.0,
                #{fallbackTime},
                #{fallbackTimeMs}
            FROM t_social_article_stats s
            INNER JOIN t_article a ON a.id = s.article_id AND a.status = 1 AND a.deleted = 0
            WHERE s.share_count > 0
            """)
    int backfillShareCounts(@Param("fallbackTime") String fallbackTime,
                            @Param("fallbackTimeMs") long fallbackTimeMs);

    @Insert("""
            INSERT INTO t_article_behavior_event (
                event_id, article_id, like_delta, comment_delta, view_delta, favorite_delta, share_delta,
                comment_like_delta, reply_like_delta, score_delta, event_time, event_time_ms
            )
            SELECT
                CONCAT('backfill-comment-like-', cl.id), c.article_id,
                0, 0, 0, 0, 0, 1, 0,
                1.0,
                COALESCE(cl.create_time, #{fallbackTime}),
                UNIX_TIMESTAMP(COALESCE(cl.create_time, #{fallbackTime})) * 1000
            FROM t_social_comment_like cl
            INNER JOIN t_social_comment c ON c.id = cl.comment_id AND c.status = 1 AND c.deleted = 0
            INNER JOIN t_article a ON a.id = c.article_id AND a.status = 1 AND a.deleted = 0
            """)
    int backfillCommentLikes(@Param("fallbackTime") String fallbackTime);

    @Insert("""
            INSERT INTO t_article_behavior_event (
                event_id, article_id, like_delta, comment_delta, view_delta, favorite_delta, share_delta,
                comment_like_delta, reply_like_delta, score_delta, event_time, event_time_ms
            )
            SELECT
                CONCAT('backfill-reply-like-', rl.id), r.article_id,
                0, 0, 0, 0, 0, 0, 1,
                1.0,
                COALESCE(rl.create_time, #{fallbackTime}),
                UNIX_TIMESTAMP(COALESCE(rl.create_time, #{fallbackTime})) * 1000
            FROM t_social_reply_like rl
            INNER JOIN t_social_reply r ON r.id = rl.reply_id AND r.status = 1 AND r.deleted = 0
            INNER JOIN t_article a ON a.id = r.article_id AND a.status = 1 AND a.deleted = 0
            """)
    int backfillReplyLikes(@Param("fallbackTime") String fallbackTime);
}
