package com.game.community.content.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.util.StringUtils;

/** 统一清洗图文正文和视频介绍富文本，避免公开详情直接渲染未信任 HTML。 */
public final class ArticleRichTextSanitizer {

    private ArticleRichTextSanitizer() {
    }

    /** 保留编辑器需要的排版标签和安全链接，移除脚本、事件属性及不安全协议。 */
    public static String sanitize(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        Safelist safelist = Safelist.relaxed()
                .addTags("h1", "h2", "h3", "u", "s", "blockquote")
                .addAttributes("a", "target", "rel")
                .removeTags("img");
        return Jsoup.clean(html, "", safelist, new org.jsoup.nodes.Document.OutputSettings()
                .prettyPrint(false));
    }
}
