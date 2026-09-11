package com.game.community.content.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.content.service.ChunkUploadService;
import com.game.community.model.dto.file.ChunkUploadInitDTO;
import com.game.community.model.vo.file.ChunkUploadInitVO;
import com.game.community.model.vo.file.ChunkUploadStatusVO;
import com.game.community.model.vo.file.MediaUploadVO;
import com.game.community.utils.MinIOUtils;
import com.game.community.utils.RedisUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 分片上传：会话存 Redis，分片落私有桶；可按文章中止上传。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkUploadServiceImpl implements ChunkUploadService {

    private static final long MIN_COMPOSE_SOURCE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final String STATUS_UPLOADING = "UPLOADING";
    private static final String STATUS_MERGED = "MERGED";
    private static final String STATUS_ABORTED = "ABORTED";

    private final RedisUtils redisUtils;
    private final MinIOUtils minIOUtils;

    @Override
    public ChunkUploadInitVO init(ChunkUploadInitDTO dto, Long userId) {
        validateInit(dto);
        String bizType = normalizeBizType(dto.getBizType());
        String fileMd5 = normalizeFileMd5(dto.getFileMd5());
        ChunkUploadInitVO globallyReusable = findGlobalReusableUpload(dto, fileMd5, userId);
        if (globallyReusable != null) {
            return globallyReusable;
        }
        ChunkUploadInitVO reusable = findReusableUpload(dto, bizType, fileMd5, userId);
        if (reusable != null) {
            return reusable;
        }
        long chunkSize = ContentConstants.MediaLimit.CHUNK_SIZE_BYTES;
        int totalChunks = (int) Math.ceil(dto.getFileSize() * 1.0 / chunkSize);
        if (totalChunks <= 0) {
            throw new BusinessException("文件分片数无效");
        }

        String uploadId = UUID.randomUUID().toString().replace("-", "");
        String prefix = "content/chunks/" + userId + "/" + uploadId + "/";

        UploadSession session = new UploadSession();
        session.setUploadId(uploadId);
        session.setUserId(userId);
        session.setFileName(dto.getFileName().trim());
        session.setFileSize(dto.getFileSize());
        session.setFileMd5(fileMd5);
        session.setContentType(StringUtils.hasText(dto.getContentType()) ? dto.getContentType().trim() : "application/octet-stream");
        session.setBizType(bizType);
        session.setArticleId(dto.getArticleId());
        session.setChunkSize(chunkSize);
        session.setTotalChunks(totalChunks);
        session.setUploadedChunks(new TreeSet<>());
        session.setPrefix(prefix);
        session.setStatus(STATUS_UPLOADING);
        saveSession(session);
        if (dto.getArticleId() != null) {
            linkArticle(dto.getArticleId(), uploadId);
        }

        ChunkUploadInitVO vo = new ChunkUploadInitVO();
        vo.setUploadId(uploadId);
        vo.setChunkSize(chunkSize);
        vo.setTotalChunks(totalChunks);
        vo.setUploadedChunks(List.of());
        vo.setInstant(false);
        return vo;
    }

    private ChunkUploadInitVO findGlobalReusableUpload(ChunkUploadInitDTO dto,
                                                       String fileMd5,
                                                       Long userId) {
        String objectKey = globalObjectKey(fileMd5, dto.getFileSize());
        if (!StringUtils.hasText(objectKey) || !minIOUtils.privateObjectExists(objectKey)) {
            return null;
        }

        long chunkSize = ContentConstants.MediaLimit.CHUNK_SIZE_BYTES;
        int totalChunks = (int) Math.ceil(dto.getFileSize() * 1.0 / chunkSize);
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        UploadSession session = new UploadSession();
        session.setUploadId(uploadId);
        session.setUserId(userId);
        session.setFileName(dto.getFileName().trim());
        session.setFileSize(dto.getFileSize());
        session.setFileMd5(fileMd5);
        session.setContentType(StringUtils.hasText(dto.getContentType())
                ? dto.getContentType().trim() : "application/octet-stream");
        session.setBizType(normalizeBizType(dto.getBizType()));
        session.setArticleId(dto.getArticleId());
        session.setChunkSize(chunkSize);
        session.setTotalChunks(totalChunks);
        session.setUploadedChunks(new TreeSet<>());
        session.setPrefix("content/chunks/" + userId + "/" + uploadId + "/");
        session.setStatus(STATUS_MERGED);
        session.setObjectKey(objectKey);
        saveSession(session);
        if (dto.getArticleId() != null) {
            linkArticle(dto.getArticleId(), uploadId);
        }
        return toInitVo(session, true);
    }

    private ChunkUploadInitVO findReusableUpload(ChunkUploadInitDTO dto,
                                                 String bizType,
                                                 String fileMd5,
                                                 Long userId) {
        if (!StringUtils.hasText(fileMd5)) {
            return null;
        }
        String indexKey = md5IndexKey(userId, fileMd5);
        String uploadId = redisUtils.get(indexKey);
        if (!StringUtils.hasText(uploadId)) {
            return null;
        }

        UploadSession session = loadSession(uploadId);
        if (session == null
                || !Objects.equals(session.getUserId(), userId)
                || !Objects.equals(session.getFileSize(), dto.getFileSize())
                || !Objects.equals(session.getFileMd5(), fileMd5)
                || !Objects.equals(session.getBizType(), bizType)) {
            redisUtils.del(indexKey);
            return null;
        }
        if (STATUS_ABORTED.equals(session.getStatus())) {
            redisUtils.del(indexKey);
            return null;
        }
        if (session.getArticleId() != null
                && dto.getArticleId() != null
                && !Objects.equals(session.getArticleId(), dto.getArticleId())) {
            return null;
        }
        if (session.getArticleId() != null && dto.getArticleId() == null) {
            return null;
        }
        if (session.getArticleId() == null && dto.getArticleId() != null) {
            session.setArticleId(dto.getArticleId());
            saveSession(session);
            linkArticle(dto.getArticleId(), session.getUploadId());
        }
        if (STATUS_MERGED.equals(session.getStatus()) && StringUtils.hasText(session.getObjectKey())) {
            return toInitVo(session, true);
        }
        if (STATUS_UPLOADING.equals(session.getStatus())) {
            return toInitVo(session, false);
        }
        return null;
    }

    @Override
    public ChunkUploadStatusVO uploadChunk(String uploadId, int chunkIndex, MultipartFile file, Long userId) {
        UploadSession session = requireOwnedSession(uploadId, userId);
        if (!STATUS_UPLOADING.equals(session.getStatus())) {
            throw new BusinessException("上传会话已结束，无法继续上传");
        }
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new BusinessException("分片序号无效");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("分片内容不能为空");
        }
        long maxChunkSize = session.getChunkSize() == null
                ? ContentConstants.MediaLimit.CHUNK_SIZE_BYTES : session.getChunkSize();
        if (file.getSize() > maxChunkSize
                || (chunkIndex < session.getTotalChunks() - 1 && file.getSize() != maxChunkSize)) {
            throw new BusinessException("分片大小不符合上传会话约定");
        }
        if (session.getUploadedChunks().contains(chunkIndex)) {
            return toStatus(session);
        }

        String objectName = chunkObjectName(session, chunkIndex);
        try {
            minIOUtils.uploadPrivateBytes(objectName, file.getBytes(),
                    StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream");
        } catch (Exception e) {
            throw new BusinessException("分片写入失败");
        }
        UploadSession updated = markChunkUploaded(uploadId, chunkIndex, userId);
        if (updated == null) {
            minIOUtils.deletePrivateObject(objectName);
            throw new BusinessException("上传会话已结束，请重新获取状态");
        }
        return toStatus(updated);
    }

    @Override
    public MediaUploadVO merge(String uploadId, Long userId) {
        String lockKey = "content:upload:merge:" + uploadId;
        String lockToken = acquireLock(lockKey);
        try {
            UploadSession session = requireOwnedSession(uploadId, userId);
            if (STATUS_MERGED.equals(session.getStatus()) && StringUtils.hasText(session.getObjectKey())) {
                return toMediaVo(session.getObjectKey());
            }
            if (!STATUS_UPLOADING.equals(session.getStatus())) {
                throw new BusinessException("上传会话状态不允许合并");
            }
            if (session.getUploadedChunks().size() != session.getTotalChunks()) {
                throw new BusinessException("分片未全部上传完成");
            }

            String existingGlobalObject = globalObjectKey(session.getFileMd5(), session.getFileSize());
            if (!StringUtils.hasText(existingGlobalObject)
                    || !minIOUtils.privateObjectExists(existingGlobalObject)) {
                existingGlobalObject = null;
            }
            if (existingGlobalObject != null) {
                minIOUtils.deletePrivatePrefix(session.getPrefix());
                session.setObjectKey(existingGlobalObject);
                session.setStatus(STATUS_MERGED);
                session.setUploadedChunks(new TreeSet<>());
                saveSession(session);
                return toMediaVo(existingGlobalObject);
            }

            List<String> sources = new ArrayList<>();
            for (int i = 0; i < session.getTotalChunks(); i++) {
                sources.add(chunkObjectName(session, i));
            }
            String suffix = extractSuffix(session.getFileName());
            String folder = "video".equals(session.getBizType()) ? "video" : "content";
            String targetObject = StringUtils.hasText(session.getFileMd5())
                    ? globalObjectKey(session.getFileMd5(), session.getFileSize())
                    : "content/" + folder + "/" + userId + "/" + uploadId + suffix;
            if (session.getChunkSize() != null
                    && session.getChunkSize() < MIN_COMPOSE_SOURCE_SIZE_BYTES) {
                minIOUtils.concatenatePrivateObjects(targetObject, sources,
                        session.getFileSize(), session.getContentType());
            } else {
                minIOUtils.composePrivateObjects(targetObject, sources);
            }
            if (StringUtils.hasText(session.getFileMd5())) {
                String actualMd5 = minIOUtils.calculatePrivateMd5(targetObject);
                if (!session.getFileMd5().equalsIgnoreCase(actualMd5)) {
                    minIOUtils.deletePrivateObject(targetObject);
                    throw new BusinessException("文件完整性校验失败，请重新上传");
                }
            }
            minIOUtils.deletePrivatePrefix(session.getPrefix());

            session.setObjectKey(targetObject);
            session.setStatus(STATUS_MERGED);
            session.setUploadedChunks(new TreeSet<>());
            saveSession(session);
            return toMediaVo(targetObject);
        } finally {
            redisUtils.unlock(lockKey, lockToken);
        }
    }

    @Override
    public void abort(String uploadId, Long userId) {
        abortSession(requireOwnedSession(uploadId, userId), true);
    }

    @Override
    public void bindArticle(String uploadId, Long articleId, Long userId) {
        UploadSession session = requireOwnedSession(uploadId, userId);
        session.setArticleId(articleId);
        saveSession(session);
        linkArticle(articleId, uploadId);
    }

    @Override
    public void abortByArticleId(Long articleId, Long userId, boolean deleteMerged) {
        if (articleId == null) {
            return;
        }
        Set<String> uploadIds = redisUtils.setMembers(articleUploadsKey(articleId));
        for (String uploadId : uploadIds) {
            try {
                UploadSession session = loadSession(uploadId);
                if (session == null) {
                    continue;
                }
                if (!Objects.equals(session.getUserId(), userId)) {
                    continue;
                }
                abortSession(session, deleteMerged);
            } catch (Exception e) {
                log.warn("按文章中止上传失败: articleId={}, uploadId={}, error={}",
                        articleId, uploadId, e.getMessage());
            }
        }
        if (deleteMerged) {
            redisUtils.del(articleUploadsKey(articleId));
        }
    }

    @Override
    public ChunkUploadStatusVO status(String uploadId, Long userId) {
        return toStatus(requireOwnedSession(uploadId, userId));
    }

    @Override
    public List<ChunkUploadStatusVO> listByArticleId(Long articleId, Long userId) {
        if (articleId == null) {
            return List.of();
        }
        Set<String> uploadIds = redisUtils.setMembers(articleUploadsKey(articleId));
        List<ChunkUploadStatusVO> result = new ArrayList<>();
        for (String uploadId : uploadIds) {
            UploadSession session = loadSession(uploadId);
            if (session == null || !Objects.equals(session.getUserId(), userId)) {
                continue;
            }
            if (STATUS_ABORTED.equals(session.getStatus())) {
                continue;
            }
            result.add(toStatus(session));
        }
        return result;
    }

    private void abortSession(UploadSession session, boolean deleteMerged) {
        if (STATUS_ABORTED.equals(session.getStatus())) {
            return;
        }
        try {
            minIOUtils.deletePrivatePrefix(session.getPrefix());
            if (deleteMerged && StringUtils.hasText(session.getObjectKey())
                    && !isGlobalObject(session.getObjectKey())) {
                minIOUtils.deletePrivateObject(session.getObjectKey());
                session.setObjectKey(null);
            }
        } catch (Exception e) {
            log.warn("清理上传残留失败: uploadId={}, error={}", session.getUploadId(), e.getMessage());
        }
        session.setStatus(STATUS_ABORTED);
        saveSession(session);
        removeMd5Index(session);
        if (session.getArticleId() != null && deleteMerged) {
            redisUtils.setRemove(articleUploadsKey(session.getArticleId()), session.getUploadId());
        }
    }

    private void linkArticle(Long articleId, String uploadId) {
        String key = articleUploadsKey(articleId);
        redisUtils.setAdd(key, uploadId);
        redisUtils.expire(key, ContentConstants.UploadRedis.SESSION_TTL_SECONDS);
    }

    private void validateInit(ChunkUploadInitDTO dto) {
        String bizType = normalizeBizType(dto.getBizType());
        if ("video".equals(bizType)) {
            if (dto.getFileSize() > ContentConstants.MediaLimit.VIDEO_MAX_BYTES) {
                throw new BusinessException("视频大小不能超过500MB");
            }
            String name = dto.getFileName().toLowerCase(Locale.ROOT);
            if (!(name.endsWith(".mp4") || name.endsWith(".webm"))) {
                throw new BusinessException("视频仅支持 mp4/webm");
            }
            if (StringUtils.hasText(dto.getContentType())
                    && !dto.getContentType().startsWith("video/")) {
                throw new BusinessException("视频 Content-Type 无效");
            }
        } else if (dto.getFileSize() > ContentConstants.MediaLimit.IMAGE_MAX_BYTES) {
            throw new BusinessException("图片大小不能超过5MB");
        }
    }

    private String normalizeBizType(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return "video";
        }
        String normalized = bizType.trim().toLowerCase(Locale.ROOT);
        if (!List.of("video", "cover", "image").contains(normalized)) {
            throw new BusinessException("不支持的上传业务类型");
        }
        return normalized;
    }

    private UploadSession requireOwnedSession(String uploadId, Long userId) {
        UploadSession session = loadSession(uploadId);
        if (session == null) {
            throw new BusinessException("上传会话不存在或已过期");
        }
        if (!Objects.equals(session.getUserId(), userId)) {
            throw new BusinessException("无权操作该上传会话");
        }
        return session;
    }

    private UploadSession loadSession(String uploadId) {
        if (!StringUtils.hasText(uploadId)) {
            return null;
        }
        String raw = redisUtils.get(sessionKey(uploadId));
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return parseSession(raw);
    }

    private void saveSession(UploadSession session) {
        redisUtils.setEx(sessionKey(session.getUploadId()), JSON.toJSONString(session),
                ContentConstants.UploadRedis.SESSION_TTL_SECONDS);
        if (StringUtils.hasText(session.getFileMd5()) && !STATUS_ABORTED.equals(session.getStatus())) {
            redisUtils.setEx(md5IndexKey(session.getUserId(), session.getFileMd5()), session.getUploadId(),
                    ContentConstants.UploadRedis.SESSION_TTL_SECONDS);
        }
    }

    /** 使用 CAS 合并分片状态，保证并发上传不同分片时不会互相覆盖。 */
    private UploadSession markChunkUploaded(String uploadId, int chunkIndex, Long userId) {
        String key = sessionKey(uploadId);
        for (int attempt = 0; attempt < 8; attempt++) {
            String raw = redisUtils.get(key);
            UploadSession session = parseSession(raw);
            if (session == null || !Objects.equals(session.getUserId(), userId)
                    || !STATUS_UPLOADING.equals(session.getStatus())) {
                return null;
            }
            if (session.getUploadedChunks().contains(chunkIndex)) {
                return session;
            }
            session.getUploadedChunks().add(chunkIndex);
            String updated = JSON.toJSONString(session);
            if (redisUtils.compareAndSet(key, raw, updated,
                    ContentConstants.UploadRedis.SESSION_TTL_SECONDS)) {
                if (StringUtils.hasText(session.getFileMd5())) {
                    redisUtils.setEx(md5IndexKey(session.getUserId(), session.getFileMd5()),
                            session.getUploadId(), ContentConstants.UploadRedis.SESSION_TTL_SECONDS);
                }
                return session;
            }
        }
        throw new BusinessException("上传状态更新繁忙，请稍后重试");
    }

    private UploadSession parseSession(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        UploadSession session = JSON.parseObject(raw, new TypeReference<UploadSession>() {
        });
        if (session != null && session.getUploadedChunks() == null) {
            session.setUploadedChunks(new TreeSet<>());
        }
        return session;
    }

    private String acquireLock(String key) {
        String token = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 30; attempt++) {
            if (Boolean.TRUE.equals(redisUtils.setIfAbsent(key, token, 60))) {
                return token;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("获取上传合并锁被中断");
            }
        }
        throw new BusinessException("上传正在合并，请稍后重试");
    }

    private String sessionKey(String uploadId) {
        return ContentConstants.UploadRedis.SESSION_PREFIX + uploadId;
    }

    private String articleUploadsKey(Long articleId) {
        return ContentConstants.UploadRedis.ARTICLE_UPLOADS_PREFIX + articleId;
    }

    private String md5IndexKey(Long userId, String fileMd5) {
        return ContentConstants.UploadRedis.MD5_INDEX_PREFIX + userId + ":" + fileMd5;
    }

    private String globalObjectKey(String fileMd5, Long fileSize) {
        if (!StringUtils.hasText(fileMd5) || fileSize == null || fileSize <= 0) {
            return null;
        }
        return ContentConstants.UploadRedis.GLOBAL_OBJECT_PREFIX + fileMd5 + "-" + fileSize;
    }

    private boolean isGlobalObject(String objectKey) {
        return StringUtils.hasText(objectKey)
                && objectKey.startsWith(ContentConstants.UploadRedis.GLOBAL_OBJECT_PREFIX);
    }

    private void removeMd5Index(UploadSession session) {
        if (!StringUtils.hasText(session.getFileMd5())) {
            return;
        }
        String indexKey = md5IndexKey(session.getUserId(), session.getFileMd5());
        if (Objects.equals(redisUtils.get(indexKey), session.getUploadId())) {
            redisUtils.del(indexKey);
        }
    }

    private ChunkUploadInitVO toInitVo(UploadSession session, boolean instant) {
        ChunkUploadInitVO vo = new ChunkUploadInitVO();
        vo.setUploadId(session.getUploadId());
        vo.setObjectKey(session.getObjectKey());
        vo.setChunkSize(session.getChunkSize());
        vo.setTotalChunks(session.getTotalChunks());
        vo.setUploadedChunks(new ArrayList<>(session.getUploadedChunks() == null
                ? Collections.emptySet() : session.getUploadedChunks()));
        vo.setInstant(instant);
        if (instant && StringUtils.hasText(session.getObjectKey())) {
            MediaUploadVO media = toMediaVo(session.getObjectKey());
            vo.setPendingUrl(media.getPendingUrl());
            vo.setPreviewUrl(media.getPreviewUrl());
        }
        return vo;
    }

    private String normalizeFileMd5(String fileMd5) {
        if (!StringUtils.hasText(fileMd5)) {
            return null;
        }
        String normalized = fileMd5.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{32}")) {
            throw new BusinessException("fileMd5 格式无效");
        }
        return normalized;
    }

    private String chunkObjectName(UploadSession session, int chunkIndex) {
        return session.getPrefix() + String.format("%06d", chunkIndex);
    }

    private ChunkUploadStatusVO toStatus(UploadSession session) {
        ChunkUploadStatusVO vo = new ChunkUploadStatusVO();
        vo.setUploadId(session.getUploadId());
        vo.setStatus(session.getStatus());
        vo.setTotalChunks(session.getTotalChunks());
        List<Integer> chunks = new ArrayList<>(session.getUploadedChunks() == null
                ? Collections.emptySet() : session.getUploadedChunks());
        Collections.sort(chunks);
        vo.setUploadedChunks(chunks);
        vo.setUploadedCount(chunks.size());
        int percent = session.getTotalChunks() <= 0 ? 0
                : (int) Math.min(100, Math.round(chunks.size() * 100.0 / session.getTotalChunks()));
        if (STATUS_MERGED.equals(session.getStatus())) {
            percent = 100;
        }
        if (STATUS_ABORTED.equals(session.getStatus())) {
            percent = 0;
        }
        vo.setPercent(percent);
        if (StringUtils.hasText(session.getObjectKey()) && !STATUS_ABORTED.equals(session.getStatus())) {
            MediaUploadVO media = toMediaVo(session.getObjectKey());
            vo.setPendingUrl(media.getPendingUrl());
            vo.setPreviewUrl(media.getPreviewUrl());
        }
        return vo;
    }

    private MediaUploadVO toMediaVo(String objectKey) {
        MediaUploadVO vo = new MediaUploadVO();
        vo.setObjectKey(objectKey);
        vo.setPendingUrl("pending://" + objectKey);
        vo.setPreviewUrl(minIOUtils.generatePrivateUrl(objectKey,
                ContentConstants.MediaLimit.PRESIGNED_EXPIRE_SECONDS));
        return vo;
    }

    private static String extractSuffix(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.'));
        }
        return "";
    }

    @Data
    private static class UploadSession {
        private String uploadId;
        private Long userId;
        private String fileName;
        private Long fileSize;
        private String fileMd5;
        private String contentType;
        private String bizType;
        private Long articleId;
        private Long chunkSize;
        private Integer totalChunks;
        private TreeSet<Integer> uploadedChunks;
        private String prefix;
        private String status;
        private String objectKey;
    }
}
