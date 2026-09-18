package com.game.community.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.dto.recommend.ArticleRankScoreAgg;
import com.game.community.model.entity.recommend.ArticleBehaviorEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ArticleBehaviorEventMapper extends BaseMapper<ArticleBehaviorEvent> {

    @Insert("""
            INSERT INTO t_article_behavior_event (
                event_id, article_id, like_delta, comment_delta, danmaku_delta, view_delta,
                favorite_delta, share_delta, comment_like_delta, reply_like_delta,
                score_delta, event_time, event_time_ms
            ) VALUES (
                #{eventId}, #{articleId}, #{likeDelta}, #{commentDelta}, #{danmakuDelta}, #{viewDelta},
                #{favoriteDelta}, #{shareDelta}, #{commentLikeDelta}, #{replyLikeDelta},
                #{scoreDelta}, #{eventTime}, #{eventTimeMs}
            )
            ON DUPLICATE KEY UPDATE event_id = #{eventId}
            """)
    int insertIgnore(ArticleBehaviorEvent event);

    @Select("""
            SELECT COALESCE(SUM(e.score_delta), 0)
            FROM t_article_behavior_event e
            WHERE e.article_id = #{articleId}
              AND e.event_time >= #{start} AND e.event_time < #{end}
            """)
    Double sumArticleScore(@Param("articleId") Long articleId,
                           @Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end);

    @Select("""
            SELECT e.article_id AS articleId, SUM(e.score_delta) AS totalScore
            FROM t_article_behavior_event e
            INNER JOIN t_article a ON e.article_id = a.id
                AND a.status = 1 AND a.deleted = 0
            WHERE e.event_time >= #{start} AND e.event_time < #{end}
            GROUP BY e.article_id
            HAVING totalScore > 0
            ORDER BY totalScore DESC
            LIMIT #{limit}
            """)
    List<ArticleRankScoreAgg> aggregateAllScope(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end,
                                                @Param("limit") int limit);

    @Select("""
            SELECT e.article_id AS articleId, SUM(e.score_delta) AS totalScore
            FROM t_article_behavior_event e
            INNER JOIN t_article a ON e.article_id = a.id AND a.deleted = 0
                AND (a.category_id = #{categoryId}
                     OR JSON_CONTAINS(COALESCE(a.category_ids, JSON_ARRAY()), CAST(#{categoryId} AS JSON), '$'))
            WHERE e.event_time >= #{start} AND e.event_time < #{end}
              AND a.status = 1
            GROUP BY e.article_id
            HAVING totalScore > 0
            ORDER BY totalScore DESC
            LIMIT #{limit}
            """)
    List<ArticleRankScoreAgg> aggregateCategoryScope(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end,
                                                     @Param("categoryId") Long categoryId,
                                                     @Param("limit") int limit);
}
