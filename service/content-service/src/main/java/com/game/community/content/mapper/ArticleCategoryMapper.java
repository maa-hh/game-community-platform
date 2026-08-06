package com.game.community.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.article.ArticleCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ArticleCategoryMapper extends BaseMapper<ArticleCategory> {

    @Select("<script>SELECT article_id, category_id FROM t_article_category "
            + "WHERE article_id IN "
            + "<foreach collection='list' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "ORDER BY article_id, id</script>")
    List<ArticleCategory> selectByArticleIds(List<Long> articleIds);

    @Select("SELECT category_id FROM t_article_category WHERE article_id = #{articleId} ORDER BY id")
    List<Long> selectCategoryIdsByArticleId(Long articleId);
}
