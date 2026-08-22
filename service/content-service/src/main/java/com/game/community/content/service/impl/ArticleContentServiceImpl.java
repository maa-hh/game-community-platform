package com.game.community.content.service.impl;

import com.game.community.content.mongo.ArticleContentRepository;
import com.game.community.content.service.ArticleContentService;
import com.game.community.model.mongo.ArticleContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文章内容服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleContentServiceImpl implements ArticleContentService {

    private final ArticleContentRepository articleContentRepository;

    private final MongoTemplate mongoTemplate;

    @Override
    public ArticleContent saveContent(Long articleId, String content, String contentHtml,
                                      Map<String, String> contentParagraphs,
                                      List<String> imageUrls, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        Query query = Query.query(Criteria.where("articleId").is(articleId));
        Update update = new Update()
                .set("content", content)
                .set("contentHtml", contentHtml)
                .set("contentParagraphs", contentParagraphs)
                .set("imageUrls", imageUrls)
                .set("updateTime", now)
                .setOnInsert("articleId", articleId)
                .setOnInsert("userId", userId)
                .setOnInsert("createTime", now);
        mongoTemplate.upsert(query, update, ArticleContent.class);
        ArticleContent saved = articleContentRepository.findByArticleId(articleId).orElseThrow(
                () -> new IllegalStateException("文章内容写入后无法读取"));
        log.info("文章内容保存MongoDB: articleId={}", articleId);
        return saved;
    }

    @Override
    public ArticleContent getByArticleId(Long articleId) {
        return articleContentRepository.findByArticleId(articleId).orElse(null);
    }

    @Override
    public void deleteByArticleId(Long articleId) {
        articleContentRepository.deleteByArticleId(articleId);
        log.info("文章内容从MongoDB删除: articleId={}", articleId);
    }
}
