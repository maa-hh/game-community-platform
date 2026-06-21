package com.game.community.content.service.impl;

import com.game.community.content.mongo.ArticleContentRepository;
import com.game.community.content.service.ArticleContentService;
import com.game.community.model.mongo.ArticleContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 文章内容服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleContentServiceImpl implements ArticleContentService {

    private final ArticleContentRepository articleContentRepository;

    @Override
    public ArticleContent saveContent(Long articleId, String content, Map<String, String> contentParagraphs,
                                      List<String> imageUrls, Long userId) {
        // 先查是否已存在
        Optional<ArticleContent> existing = articleContentRepository.findByArticleId(articleId);

        ArticleContent articleContent;
        if (existing.isPresent()) {
            articleContent = existing.get();
            articleContent.setContent(content);
            articleContent.setContentParagraphs(contentParagraphs);
            articleContent.setImageUrls(imageUrls);
            articleContent.setUpdateTime(LocalDateTime.now());
        } else {
            articleContent = new ArticleContent();
            articleContent.setArticleId(articleId);
            articleContent.setContent(content);
            articleContent.setContentParagraphs(contentParagraphs);
            articleContent.setImageUrls(imageUrls);
            articleContent.setUserId(userId);
            articleContent.setCreateTime(LocalDateTime.now());
            articleContent.setUpdateTime(LocalDateTime.now());
        }

        ArticleContent saved = articleContentRepository.save(articleContent);
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
