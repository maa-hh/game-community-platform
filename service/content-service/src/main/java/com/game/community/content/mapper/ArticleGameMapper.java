package com.game.community.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.dto.game.ArticleGameDiscussCountDTO;
import com.game.community.model.entity.game.ArticleGame;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ArticleGameMapper extends BaseMapper<ArticleGame> {

    @Delete("DELETE FROM t_article_game WHERE article_id = #{articleId}")
    int deleteByArticleId(@Param("articleId") Long articleId);

    @Select("SELECT game_app_id FROM t_article_game WHERE article_id = #{articleId} ORDER BY id")
    List<Long> selectAppIdsByArticleId(@Param("articleId") Long articleId);

    @Select("<script>SELECT id, article_id, game_app_id, create_time FROM t_article_game "
            + "WHERE article_id IN "
            + "<foreach collection='articleIds' item='articleId' open='(' separator=',' close=')'>#{articleId}</foreach> "
            + "ORDER BY article_id, id</script>")
    List<ArticleGame> selectByArticleIds(@Param("articleIds") List<Long> articleIds);

    @Select("SELECT ag.article_id FROM t_article_game ag "
            + "INNER JOIN t_article a ON a.id = ag.article_id "
            + "WHERE ag.game_app_id = #{appId} AND a.status = 1 AND a.deleted = 0 "
            + "ORDER BY a.published_time DESC, ag.article_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Long> selectArticleIdsByAppId(@Param("appId") Long appId,
                                       @Param("offset") long offset,
                                       @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_article_game ag "
            + "INNER JOIN t_article a ON a.id = ag.article_id "
            + "WHERE ag.game_app_id = #{appId} AND a.status = 1 AND a.deleted = 0")
    long countPublishedByAppId(@Param("appId") Long appId);

    @Select("<script>"
            + "SELECT ag.game_app_id AS appId, COUNT(*) AS discussCount "
            + "FROM t_article_game ag "
            + "INNER JOIN t_article a ON a.id = ag.article_id "
            + "WHERE ag.game_app_id IN "
            + "<foreach collection='appIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "AND a.status = 1 AND a.deleted = 0 "
            + "GROUP BY ag.game_app_id"
            + "</script>")
    List<ArticleGameDiscussCountDTO> countPublishedGroupByAppIds(@Param("appIds") List<Long> appIds);
}
