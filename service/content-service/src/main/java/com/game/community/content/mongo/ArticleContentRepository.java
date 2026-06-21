package com.game.community.content.mongo;

import com.game.community.model.mongo.ArticleContent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 文章内容MongoDB Repository
 */
@Repository
public interface ArticleContentRepository extends MongoRepository<ArticleContent, String> {

    /**
     * 根据文章ID查询内容
     */
    Optional<ArticleContent> findByArticleId(Long articleId);

    /**
     * 根据文章ID删除内容
     */
    void deleteByArticleId(Long articleId);
}
