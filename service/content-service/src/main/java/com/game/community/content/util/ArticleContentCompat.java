package com.game.community.content.util;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图文帖正文纯文字 + 封面多图的数据兼容工具。
 */
public final class ArticleContentCompat {

    private static final Pattern BODY_IMAGE_MARKER = Pattern.compile("\\[图片:\\d+(?::w=\\d+)?(?::h=\\d+)?\\]");
    private static final Pattern HTML_IMG = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);

    private ArticleContentCompat() {
    }

    public record NormalizedImageText(
            String content,
            Map<String, String> contentParagraphs,
            List<String> imageUrls,
            String coverUrl
    ) {
    }

    public static boolean hasBodyImageMarkers(String content) {
        return StringUtils.hasText(content) && BODY_IMAGE_MARKER.matcher(content).find();
    }

    public static int countBodyImageMarkers(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        Matcher matcher = BODY_IMAGE_MARKER.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count += 1;
        }
        return count;
    }

    public static String stripBodyImageMarkers(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return BODY_IMAGE_MARKER.matcher(content)
                .replaceAll("")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    public static NormalizedImageText normalizeImageTextSave(
            String content,
            Map<String, String> contentParagraphs,
            List<String> imageUrls,
            String coverUrl) {
        String plainContent = stripBodyImageMarkers(content);
        Map<String, String> paragraphs = sanitizeParagraphs(contentParagraphs, plainContent);
        List<String> covers = mergeCoverImages(imageUrls, coverUrl);
        String resolvedCover = covers.isEmpty() ? null : covers.get(0);
        return new NormalizedImageText(plainContent, paragraphs, covers, resolvedCover);
    }

    /**
     * 旧数据迁移：去掉正文插图标记，并把原正文图并入封面列表。
     */
    public static NormalizedImageText migrateLegacyContent(
            String content,
            Map<String, String> contentParagraphs,
            List<String> imageUrls,
            String coverUrl) {
        int markerCount = countBodyImageMarkers(content);
        List<String> merged = mergeCoverImages(imageUrls, coverUrl);
        if (markerCount > 0 && merged.size() >= markerCount) {
            List<String> bodyUrls = new ArrayList<>(merged.subList(merged.size() - markerCount, merged.size()));
            List<String> coverUrls = new ArrayList<>(merged.subList(0, merged.size() - markerCount));
            coverUrls.addAll(bodyUrls);
            merged = dedupeUrls(coverUrls);
        } else if (contentParagraphs != null && !contentParagraphs.isEmpty()) {
            List<String> htmlImages = extractHtmlImageUrls(contentParagraphs);
            if (!htmlImages.isEmpty()) {
                List<String> next = new ArrayList<>(merged);
                next.addAll(htmlImages);
                merged = dedupeUrls(next);
            }
        }
        String plainContent = stripBodyImageMarkers(content);
        Map<String, String> paragraphs = sanitizeParagraphs(contentParagraphs, plainContent);
        String resolvedCover = merged.isEmpty() ? null : merged.get(0);
        return new NormalizedImageText(plainContent, paragraphs, merged, resolvedCover);
    }

    private static Map<String, String> sanitizeParagraphs(
            Map<String, String> contentParagraphs,
            String fallbackContent) {
        Map<String, String> result = new LinkedHashMap<>();
        if (contentParagraphs != null && !contentParagraphs.isEmpty()) {
            contentParagraphs.forEach((key, value) -> {
                if (!StringUtils.hasText(value)) {
                    return;
                }
                String plain = stripHtmlImages(stripBodyImageMarkers(value.trim()));
                if (StringUtils.hasText(plain)) {
                    String normalizedKey = StringUtils.hasText(key) ? key.trim() : "p" + (result.size() + 1);
                    result.put(normalizedKey, plain);
                }
            });
        }
        if (result.isEmpty() && StringUtils.hasText(fallbackContent)) {
            result.put("p1", fallbackContent);
        }
        return result;
    }

    private static String stripHtmlImages(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return HTML_IMG.matcher(value).replaceAll("").trim();
    }

    private static List<String> extractHtmlImageUrls(Map<String, String> paragraphs) {
        List<String> urls = new ArrayList<>();
        paragraphs.values().forEach((paragraph) -> {
            Matcher matcher = HTML_IMG.matcher(paragraph);
            while (matcher.find()) {
                if (StringUtils.hasText(matcher.group(1))) {
                    urls.add(matcher.group(1).trim());
                }
            }
        });
        return dedupeUrls(urls);
    }

    private static List<String> mergeCoverImages(List<String> imageUrls, String coverUrl) {
        List<String> merged = new ArrayList<>();
        if (StringUtils.hasText(coverUrl)) {
            merged.add(coverUrl.trim());
        }
        if (imageUrls != null) {
            imageUrls.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(merged::add);
        }
        return dedupeUrls(merged);
    }

    private static List<String> dedupeUrls(List<String> urls) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String url : urls) {
            if (StringUtils.hasText(url)) {
                seen.add(url.trim());
            }
        }
        return new ArrayList<>(seen);
    }
}
