package com.game.community.search.ai;

import com.game.community.common.constant.search.SearchConstants;
import com.game.community.model.elasticsearch.ArticleDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public final class ArticleEmbeddingTextBuilder {

    private ArticleEmbeddingTextBuilder() {
    }

    public static String build(ArticleDocument document) {
        if (document == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        append(builder, document.getTitle());
        append(builder, document.getSummary());
        if (!org.springframework.util.CollectionUtils.isEmpty(document.getCategoryNames())) {
            append(builder, String.join(" ", document.getCategoryNames()));
        } else {
            append(builder, document.getCategoryName());
        }
        if (!org.springframework.util.CollectionUtils.isEmpty(document.getGameTags())) {
            append(builder, document.getGameTags().stream()
                    .map(tag -> tag == null ? null : tag.getName())
                    .filter(StringUtils::hasText)
                    .reduce((left, right) -> left + " " + right)
                    .orElse(null));
        }
        // 正文保留摘要片段，兼顾语义召回能力与 embedding 成本，避免长正文噪声挤掉标题和标签。
        append(builder, limit(document.getContent(), 1200));
        String text = builder.toString().trim();
        if (text.length() <= SearchConstants.EMBED_TEXT_MAX_LEN) {
            return text;
        }
        return text.substring(0, SearchConstants.EMBED_TEXT_MAX_LEN);
    }

    private static void append(StringBuilder builder, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(value.trim());
    }

    private static String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
