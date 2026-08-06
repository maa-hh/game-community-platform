package com.game.community.content.service.impl;

import com.game.community.common.constant.content.ContentConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.content.config.SharePageProperties;
import com.game.community.content.service.ArticleService;
import com.game.community.content.service.SharePageService;
import com.game.community.model.entity.article.Article;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SharePageServiceImpl implements SharePageService {

    private final ArticleService articleService;
    private final SharePageProperties sharePageProperties;

    @Override
    public String renderSharePage(String publicId) {
        Article article = articleService.getByPublicId(publicId);
        if (article == null
                || (article.getDeleted() != null && article.getDeleted() == 1)
                || !Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
            throw new BusinessException("文章不存在");
        }

        String title = escapeHtml(defaultText(article.getTitle(), "游戏社区帖子"));
        String description = escapeHtml(defaultText(article.getSummary(), title));
        String image = escapeHtml(resolveOgImage(article.getCoverUrl()));
        String frontendBase = trimTrailingSlash(sharePageProperties.getFrontendBaseUrl());
        String shareBase = trimTrailingSlash(sharePageProperties.getShareBaseUrl());
        String siteName = escapeHtml(defaultText(sharePageProperties.getSiteName(), "游戏社区"));
        String postUrl = escapeHtml(frontendBase + "/post/" + article.getPublicId());
        String sharePageUrl = escapeHtml(shareBase + "/share/post/" + article.getPublicId());

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>%s</title>
                  <meta name="description" content="%s" />
                  <meta property="og:type" content="article" />
                  <meta property="og:site_name" content="%s" />
                  <meta property="og:title" content="%s" />
                  <meta property="og:description" content="%s" />
                  <meta property="og:image" content="%s" />
                  <meta property="og:url" content="%s" />
                  <meta name="twitter:card" content="summary_large_image" />
                  <meta name="twitter:title" content="%s" />
                  <meta name="twitter:description" content="%s" />
                  <meta name="twitter:image" content="%s" />
                  <meta http-equiv="refresh" content="0;url=%s" />
                </head>
                <body>
                  <article style="max-width:480px;margin:40px auto;padding:16px;font-family:sans-serif;">
                    %s
                    <h1 style="font-size:18px;margin:12px 0 8px;">%s</h1>
                    <p style="color:#666;font-size:14px;line-height:1.6;">%s</p>
                    <p style="font-size:12px;color:#999;">%s</p>
                    <p><a href="%s">查看帖子详情</a></p>
                  </article>
                </body>
                </html>
                """.formatted(
                title,
                description,
                siteName,
                title,
                description,
                image,
                sharePageUrl,
                title,
                description,
                image,
                postUrl,
                buildCoverImgTag(image),
                title,
                description,
                siteName,
                postUrl);
    }

    private String buildCoverImgTag(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return "";
        }
        return "<img src=\"" + imageUrl + "\" alt=\"\" style=\"width:100%%;border-radius:8px;object-fit:cover;max-height:240px;\" />";
    }

    private String resolveOgImage(String coverUrl) {
        if (StringUtils.hasText(coverUrl)) {
            String trimmed = coverUrl.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed;
            }
        }
        String fallback = sharePageProperties.getDefaultImageUrl();
        return StringUtils.hasText(fallback) ? fallback.trim() : "";
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String trimTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return "http://localhost:3000";
        }
        String normalized = url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
