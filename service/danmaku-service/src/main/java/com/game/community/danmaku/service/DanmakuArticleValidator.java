package com.game.community.danmaku.service;

import com.game.community.common.constant.content.ContentConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.feign.ContentFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.vo.article.ArticleListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class DanmakuArticleValidator {

    private final ContentFeignClient contentFeignClient;
    private final Map<String, CachedArticle> cache = new ConcurrentHashMap<>();

    public ArticleListVO requireVideo(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            throw new BusinessException("视频帖子不存在");
        }
        CachedArticle cached = cache.get(publicId);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return cached.article;
        }
        Result<ArticleListVO> result = contentFeignClient.getArticleByPublicId(publicId);
        ArticleListVO article = result == null ? null : result.getData();
        if (article == null
                || !Integer.valueOf(ContentConstants.PostType.VIDEO).equals(article.getPostType())
                || !Integer.valueOf(ContentConstants.ArticleStatus.PUBLISHED).equals(article.getStatus())) {
            throw new BusinessException("只有已发布的视频帖子支持弹幕");
        }
        cache.put(publicId, new CachedArticle(article, System.currentTimeMillis() + 30_000));
        return article;
    }

    private record CachedArticle(ArticleListVO article, long expiresAt) {
    }
}
