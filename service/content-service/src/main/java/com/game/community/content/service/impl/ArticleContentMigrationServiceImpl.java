package com.game.community.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.content.mapper.ArticleMapper;
import com.game.community.content.mongo.ArticleContentRepository;
import com.game.community.content.service.ArticleContentMigrationService;
import com.game.community.content.util.ArticleContentCompat;
import com.game.community.model.entity.article.Article;
import com.game.community.model.mongo.ArticleContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleContentMigrationServiceImpl implements ArticleContentMigrationService {

    private final ArticleContentRepository articleContentRepository;
    private final ArticleMapper articleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int migrateLegacyImageTextContent() {
        List<ArticleContent> contents = articleContentRepository.findAll();
        int updated = 0;
        for (ArticleContent content : contents) {
            if (content.getArticleId() == null) {
                continue;
            }
            Article article = articleMapper.selectById(content.getArticleId());
            if (article == null || Objects.equals(article.getDeleted(), 1)) {
                continue;
            }
            Integer postType = article.getPostType();
            if (postType == null
                    || (postType != ContentConstants.PostType.IMAGE_TEXT
                    && postType != ContentConstants.PostType.ARTICLE)) {
                continue;
            }

            ArticleContentCompat.NormalizedImageText migrated = ArticleContentCompat.migrateLegacyContent(
                    content.getContent(),
                    content.getContentParagraphs(),
                    content.getImageUrls(),
                    article.getCoverUrl());

            boolean contentChanged = !Objects.equals(content.getContent(), migrated.content())
                    || !Objects.equals(content.getContentParagraphs(), migrated.contentParagraphs())
                    || !Objects.equals(content.getImageUrls(), migrated.imageUrls());
            if (contentChanged) {
                content.setContent(migrated.content());
                content.setContentParagraphs(migrated.contentParagraphs());
                content.setImageUrls(migrated.imageUrls());
                content.setUpdateTime(LocalDateTime.now());
                articleContentRepository.save(content);
            }

            boolean articleChanged = false;
            if (Objects.equals(postType, ContentConstants.PostType.ARTICLE)) {
                article.setPostType(ContentConstants.PostType.IMAGE_TEXT);
                articleChanged = true;
            }
            if (!Objects.equals(article.getCoverUrl(), migrated.coverUrl())) {
                article.setCoverUrl(migrated.coverUrl());
                articleChanged = true;
            }
            if (articleChanged) {
                article.setUpdateTime(LocalDateTime.now());
                articleMapper.updateById(article);
            }

            if (contentChanged || articleChanged) {
                updated += 1;
                log.info("图文内容迁移完成: articleId={}", article.getId());
            }
        }

        int mysqlOnly = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getPostType, ContentConstants.PostType.ARTICLE)
                .set(Article::getPostType, ContentConstants.PostType.IMAGE_TEXT)
                .set(Article::getUpdateTime, LocalDateTime.now()));
        if (mysqlOnly > 0) {
            log.info("已将 {} 条 post_type=2 记录归并为图文", mysqlOnly);
        }
        return updated;
    }
}
