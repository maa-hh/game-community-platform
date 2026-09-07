package com.game.community.content.util;

import com.game.community.common.constant.content.ContentConstants;
import com.game.community.utils.MinIOUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 帖子媒体引用：pending://objectKey ↔ 公网 URL
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleMediaHelper {

    public static final String PENDING_PREFIX = "pending://";

    private final MinIOUtils minIOUtils;

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
        String privateObjectName = minIOUtils.parsePrivateObjectName(ref);
        if (StringUtils.hasText(privateObjectName)) {
            return minIOUtils.generatePrivateUrl(privateObjectName,
                    ContentConstants.MediaLimit.PRESIGNED_EXPIRE_SECONDS);
        }
        return ref;
    }

    /** 将公共媒体地址按当前 MINIO_PUBLIC_ENDPOINT 动态解析。 */
    public String resolvePublic(String ref) {
        return minIOUtils.resolvePublicUrl(ref);
    }

    /** 将作者接口收到的私有 URL 归一化为可持久化的 pending:// 引用。 */
    public String normalizeReference(String ref) {
        if (!StringUtils.hasText(ref) || isPending(ref)) {
            return ref;
        }
        String privateObjectName = minIOUtils.parsePrivateObjectName(ref);
        return StringUtils.hasText(privateObjectName) ? PENDING_PREFIX + privateObjectName : ref;
    }

    /** 将公共媒体移回私有桶；外部资源保持原 URL。 */
    public String demoteToPrivate(String ref, String folder) {
        if (!StringUtils.hasText(ref) || isPending(ref)) {
            return ref;
        }
        String normalized = normalizeReference(ref);
        if (isPending(normalized)) {
            return normalized;
        }
        String privateObjectName = minIOUtils.movePublicObjectToPrivate(ref, folder);
        return StringUtils.hasText(privateObjectName) ? PENDING_PREFIX + privateObjectName : ref;
    }

    /** 封面与图集去重转私有，避免同一对象被移动两次。 */
    public DemotedGallery demoteGallery(String coverUrl, List<String> imageUrls, String folder) {
        Map<String, String> demoted = new LinkedHashMap<>();
        String trimmedCover = StringUtils.hasText(coverUrl) ? coverUrl.trim() : null;
        String privateCover = demoteToPrivate(trimmedCover, folder);
        if (StringUtils.hasText(trimmedCover)) {
            demoted.put(trimmedCover, privateCover);
        }

        List<String> privateImages = new ArrayList<>();
        if (imageUrls != null) {
            for (String ref : imageUrls) {
                if (!StringUtils.hasText(ref)) {
                    continue;
                }
                String key = ref.trim();
                privateImages.add(demoted.computeIfAbsent(key, k -> demoteToPrivate(k, folder)));
            }
        }
        return new DemotedGallery(privateCover, privateImages);
    }

    public String promoteToPublic(String ref, String folder) {
        if (!StringUtils.hasText(ref)) {
            return ref;
        }
        if (!isPending(ref)) {
            return ref;
        }
        String objectKey = toObjectKey(ref);
        return minIOUtils.publishPrivateObject(objectKey, folder);
    }

    /** 发布事务成功后再清理 pending 源对象，避免数据库失败造成媒体丢失。 */
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
        }
    }

    public List<String> promoteList(List<String> refs, String folder) {
        if (refs == null || refs.isEmpty()) {
            return refs;
        }
        List<String> result = new ArrayList<>(refs.size());
        for (String ref : refs) {
            result.add(promoteToPublic(ref, folder));
        }
        return result;
    }

    /**
     * 封面与图集可能包含同一 pending 引用，需去重提升，避免第二次读已删除的私有对象。
     */
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

    public record PromotedGallery(String coverUrl, List<String> imageUrls) {
    }

    public record DemotedGallery(String coverUrl, List<String> imageUrls) {
    }
}
