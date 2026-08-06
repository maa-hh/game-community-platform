package com.game.community.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.search.SuggestTerm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SuggestTermMapper extends BaseMapper<SuggestTerm> {

    @Insert("""
            INSERT INTO t_suggest_term
                (term, source_type, source_article_id, weight, pinned, status, trigger_count, created_at, updated_at)
            VALUES
                (#{term}, #{sourceType}, #{sourceArticleId}, #{weight}, #{pinned}, 'ACTIVE', 0, #{now}, #{now})
            ON DUPLICATE KEY UPDATE
                weight = GREATEST(weight, #{weight}),
                pinned = GREATEST(pinned, #{pinned}),
                status = IF(status = 'DISABLED', status, 'ACTIVE'),
                updated_at = #{now}
            """)
    int upsert(@Param("term") String term,
               @Param("sourceType") String sourceType,
               @Param("sourceArticleId") Long sourceArticleId,
               @Param("weight") Integer weight,
               @Param("pinned") Integer pinned,
               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE t_suggest_term
            SET trigger_count = trigger_count + 1,
                last_triggered_at = #{triggeredAt},
                updated_at = #{triggeredAt}
            WHERE id = #{id} AND status = 'ACTIVE'
            """)
    int incrementTrigger(@Param("id") Long id, @Param("triggeredAt") LocalDateTime triggeredAt);

    @Update("""
            UPDATE t_suggest_term
            SET status = 'EXPIRED', updated_at = #{now}
            WHERE status = 'ACTIVE'
              AND pinned = 0
              AND source_type = #{sourceType}
              AND created_at < #{observeBefore}
              AND last_triggered_at < #{coldBefore}
            """)
    int expireColdTerms(@Param("sourceType") String sourceType,
                        @Param("observeBefore") LocalDateTime observeBefore,
                        @Param("coldBefore") LocalDateTime coldBefore,
                        @Param("now") LocalDateTime now);
}
