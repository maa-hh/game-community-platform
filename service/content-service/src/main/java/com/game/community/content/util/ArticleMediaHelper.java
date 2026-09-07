package com.game.community.content.util;

import com.game.community.common.constant.content.ContentConstants;
import com.game.community.utils.MinIOUtils;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 帖子媒体引用：pending://objectKey ↔ 公网 URL。
 * 下架时公有桶资源不搬运，只通过 Redis 黑名单控制 API 返回。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleMediaHelper {

    public static final String PENDING_PREFIX = "pending://";

    private final MinIOUtils minIOUtils;

    private final RedisUtils redisUtils;

    public boolean isPending(String ref) {
        return StringUtils.hasText(ref) && ref.startsWith(PENDING_PREFIX);
    }

    public String toObjectKey(String pendingUrl) {
        if (!isPending(pendingUrl)) {
            return null;
        }
        return pendingUrl.substring(PENDING_PREFIX.length());
    }

    public String resolvePreview(String ref) {
        if (!StringUtils.hasText(ref)) {
            return ref;
        }
        if (isPending(ref)) {
            return minIOUtils.generatePrivateUrl(toObjectKey(ref),
                    ContentConstants.MediaLimit.PRESIGNED_EXPIRE_SECONDS);
        }
        return ref;
    }

    /** 解析对外媒体地址；黑名单中的资源默认不返回。 */
    public String resolvePublic(String ref) {
        return resolvePublic(ref, false);
    }

    /** 作者/管理员查看自己的下架内容时允许返回黑名单资源。 */
    public String resolvePublic(String ref, boolean allowBlacklisted) {
        if (!StringUtils.hasText(ref)) {
            return ref;
        }
        if (!allowBlacklisted && isBlacklisted(ref)) {
            return null;
        }
        if (isPending(ref)) {
            return null;
        }
        return minIOUtils.resolvePublicUrl(ref);
    }

    /** 将待审私有对象复制到公有桶；已是公有 URL 的引用保持不变。 */
    public String promoteToPublic(String ref, String folder) {
        if (!StringUtils.hasText(ref) || !isPending(ref)) {
            return ref;
        }
        return minIOUtils.publishPrivateObject(toObjectKey(ref), folder);
    }

    /** 在确认媒体不再被文章引用时清理私有对象。 */
    public void deletePendingRefs(List<String> refs) {
        if (refs == null) {
            return;
        }
        for (String ref : refs) {
            if (!isPending(ref)) {
                continue;
            }
            try {
                minIOUtils.deletePrivateObject(toObjectKey(ref));
                removeFromBlacklist(ref);
            } catch (Exception e) {
                log.warn("清理已发布 pending 媒体失败: ref={}, error={}", ref, e.getMessage());
            }
        }
    }

    public void deleteRef(String ref) {
        if (!StringUtils.hasText(ref)) {
            return;
        }
        try {
            if (isPending(ref)) {
                minIOUtils.deletePrivateObject(toObjectKey(ref));
            } else {
                minIOUtils.deleteFileByUrl(ref);
            }
            removeFromBlacklist(ref);
        } catch (Exception e) {
            log.warn("删除媒体失败: ref={}, error={}", ref, e.getMessage());
        }
    }

    public void deleteRefs(List<String> refs) {
        if (refs == null) {
            return;
        }
        for (String ref : refs) {
            deleteRef(ref);
        }
    }

    /** Outbox 清理使用严格模式，任一对象删除失败都抛出异常，交给 Outbox 重试。 */
    public void deleteRefsStrict(List<String> refs) {
        if (refs == null) {
            return;
        }
        for (String ref : refs) {
            if (!StringUtils.hasText(ref)) {
                continue;
            }
            if (isPending(ref)) {
                minIOUtils.deletePrivateObject(toObjectKey(ref));
            } else {
                minIOUtils.deleteFileByUrl(ref);
            }
            removeFromBlacklist(ref);
        }
    }

    /** 封面与图集可能包含同一私有引用，需去重，避免重复复制对象。 */
    public PromotedGallery promoteGallery(String coverUrl, List<String> imageUrls, String folder) {
        Map<String, String> promoted = new LinkedHashMap<>();
        String trimmedCover = StringUtils.hasText(coverUrl) ? coverUrl.trim() : null;
        String publicCover = promoteToPublic(trimmedCover, folder);
        if (StringUtils.hasText(trimmedCover)) {
            promoted.put(trimmedCover, StringUtils.hasText(publicCover) ? publicCover : trimmedCover);
        }

        List<String> publicImages = new ArrayList<>();
        if (imageUrls != null) {
            for (String ref : imageUrls) {
                if (!StringUtils.hasText(ref)) {
                    continue;
                }
                String key = ref.trim();
                publicImages.add(promoted.computeIfAbsent(key, k -> promoteToPublic(k, folder)));
            }
        }
        return new PromotedGallery(publicCover, publicImages);
    }

    /** 发布时把内容媒体复制到公有桶。 */
    public PromotedGallery promoteGallery(String coverUrl, List<String> imageUrls) {
        return promoteGallery(coverUrl, imageUrls, "content");
    }

    public void blacklistRefs(List<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return;
        }
        List<String> keys = refs.stream()
                .filter(StringUtils::hasText)
                .map(this::blacklistKey)
                .distinct()
                .toList();
        if (!keys.isEmpty()) {
            redisUtils.setAdd(ContentConstants.OFFLINE_MEDIA_BLACKLIST_KEY,
                    keys.toArray(String[]::new));
        }
    }

    public void removeFromBlacklist(List<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return;
        }
        refs.stream()
                .filter(StringUtils::hasText)
                .map(this::blacklistKey)
                .distinct()
                .forEach(this::removeFromBlacklist);
    }

    private void removeFromBlacklist(String ref) {
        redisUtils.setRemove(ContentConstants.OFFLINE_MEDIA_BLACKLIST_KEY, blacklistKey(ref));
    }

    private boolean isBlacklisted(String ref) {
        return redisUtils.setContains(ContentConstants.OFFLINE_MEDIA_BLACKLIST_KEY, blacklistKey(ref));
    }

    private String blacklistKey(String ref) {
        String normalized = ref.trim();
        if (isPending(normalized)) {
            return normalized;
        }
        return minIOUtils.resolvePublicUrl(normalized);
    }

    public record PromotedGallery(String coverUrl, List<String> imageUrls) {
    }
}
