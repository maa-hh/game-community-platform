package com.game.community.search.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.search.SuggestTermSource;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SuggestTermSourceMapper extends BaseMapper<SuggestTermSource> {

    @Insert("""
            INSERT INTO t_suggest_term_source (term_id, source_type, source_article_id, created_at)
            VALUES (#{termId}, #{sourceType}, #{sourceArticleId}, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE term_id = VALUES(term_id)
            """)
    int upsert(@Param("termId") Long termId,
               @Param("sourceType") String sourceType,
               @Param("sourceArticleId") Long sourceArticleId);

    @Select("""
            <script>
            SELECT DISTINCT term_id
            FROM t_suggest_term_source
            WHERE source_article_id = #{articleId}
              AND source_type IN
              <foreach collection='sourceTypes' item='sourceType' open='(' separator=',' close=')'>
                #{sourceType}
              </foreach>
            </script>
            """)
    List<Long> selectTermIdsByArticleAndTypes(@Param("articleId") Long articleId,
                                              @Param("sourceTypes") Collection<String> sourceTypes);

    @Delete("""
            <script>
            DELETE FROM t_suggest_term_source
            WHERE source_article_id = #{articleId}
              AND source_type IN
              <foreach collection='sourceTypes' item='sourceType' open='(' separator=',' close=')'>
                #{sourceType}
              </foreach>
            </script>
            """)
    int deleteByArticleAndTypes(@Param("articleId") Long articleId,
                                @Param("sourceTypes") Collection<String> sourceTypes);

    @Select("SELECT COUNT(*) FROM t_suggest_term_source WHERE term_id = #{termId}")
    long countByTermId(@Param("termId") Long termId);

    @Delete("DELETE FROM t_suggest_term_source WHERE source_article_id = #{articleId}")
    int deleteByArticle(@Param("articleId") Long articleId);
}
