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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * 分片上传：会话存 Redis，分片落私有桶；可按文章中止上传。
 */
@Slf4j
@Service
public class ChunkUploadServiceImpl implements ChunkUploadService {

    private static final String STATUS_UPLOADING = "UPLOADING";
    private static final String STATUS_MERGED = "MERGED";
    private static final String STATUS_ABORTED = "ABORTED";

    private final RedisUtils redisUtils;
    private final MinIOUtils minIOUtils;
    private final ThreadPoolTaskExecutor chunkUploadExecutor;

    public ChunkUploadServiceImpl(RedisUtils redisUtils,
                                  MinIOUtils minIOUtils,
                                  @Qualifier("contentChunkUploadExecutor")
                                  ThreadPoolTaskExecutor chunkUploadExecutor) {
        this.redisUtils = redisUtils;
        this.minIOUtils = minIOUtils;
        this.chunkUploadExecutor = chunkUploadExecutor;
    }

    @Override
    public ChunkUploadInitVO init(ChunkUploadInitDTO dto, Long userId) {
        validateInit(dto);
        String bizType = normalizeBizType(dto.getBizType());
        String fileMd5 = normalizeFileMd5(dto.getFileMd5());
        String initLockKey = StringUtils.hasText(fileMd5)
                ? initLockKey(userId, fileMd5, dto.getFileSize()) : null;
        String lockToken = initLockKey == null ? null
                : acquireLock(initLockKey, ContentConstants.UploadRedis.INIT_LOCK_SECONDS);
        try {
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
            session.setContentType(StringUtils.hasText(dto.getContentType())
                    ? dto.getContentType().trim() : "application/octet-stream");
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
        } finally {
            if (initLockKey != null) {
                redisUtils.unlock(initLockKey, lockToken);
            }
        }
    }

    private ChunkUploadInitVO findGlobalReusableUpload(ChunkUploadInitDTO dto,
                                                       String fileMd5,
                                                       Long userId) {
        String objectKey = globalObjectKey(fileMd5, dto.getFileSize());
        if (!StringUtils.hasText(objectKey)
                || !minIOUtils.privateObjectMatchesSize(objectKey, dto.getFileSize())) {
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
        if (isChunkUploaded(session, chunkIndex)) {
            return toStatus(session);
        }

        String objectName = chunkObjectName(session, chunkIndex);
        try {
            uploadChunkToMinIO(objectName, file);
        } catch (Exception e) {
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("分片写入失败");
        }
        if (!markChunkUploaded(uploadId, chunkIndex, userId)) {
            minIOUtils.deletePrivateObject(objectName);
            throw new BusinessException("上传会话已结束，请重新获取状态");
        }
        return toStatus(requireOwnedSession(uploadId, userId));
    }

    /** 将单个分片交给独立线程池写入 MinIO，限制单实例的后端并发度。 */
    private void uploadChunkToMinIO(String objectName, MultipartFile file) {
        Future<?> future;
        try {
            future = chunkUploadExecutor.submit(() -> {
                try {
                    minIOUtils.uploadPrivateStream(objectName, file.getInputStream(), file.getSize(),
                            file.getContentType());
                } catch (IOException e) {
                    throw new BusinessException("读取分片失败");
                }
            });
        } catch (RejectedExecutionException e) {
            throw new BusinessException(429, "上传并发已满，请稍后重试");
        }
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("分片上传被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException("分片写入失败");
        }
    }

    @Override
    public MediaUploadVO merge(String uploadId, Long userId) {
        String lockKey = sessionLockKey(uploadId);
        String lockToken = acquireLock(lockKey, ContentConstants.UploadRedis.SESSION_LOCK_SECONDS);
        try {
            UploadSession session = requireOwnedSession(uploadId, userId);
            if (STATUS_MERGED.equals(session.getStatus()) && StringUtils.hasText(session.getObjectKey())) {
                return toMediaVo(session.getObjectKey());
            }
            if (!STATUS_UPLOADING.equals(session.getStatus())) {
                throw new BusinessException("上传会话状态不允许合并");
            }
            if (uploadedChunkIndexes(session).size() != session.getTotalChunks()) {
                throw new BusinessException("分片未全部上传完成");
            }

            String existingGlobalObject = globalObjectKey(session.getFileMd5(), session.getFileSize());
            if (!StringUtils.hasText(existingGlobalObject)
                    || !minIOUtils.privateObjectMatchesSize(existingGlobalObject, session.getFileSize())) {
                existingGlobalObject = null;
            }
            if (existingGlobalObject != null) {
                minIOUtils.deletePrivatePrefix(session.getPrefix());
                session.setObjectKey(existingGlobalObject);
                session.setStatus(STATUS_MERGED);
                session.setUploadedChunks(new TreeSet<>());
                saveSession(session);
                redisUtils.del(uploadedChunksKey(session.getUploadId()));
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
            String mergeObject = StringUtils.hasText(session.getFileMd5())
                    ? session.getPrefix() + "merged" + suffix : targetObject;
            if (session.getChunkSize() != null
                    && session.getChunkSize() < ContentConstants.MediaLimit.MIN_COMPOSE_SOURCE_SIZE_BYTES) {
                minIOUtils.concatenatePrivateObjects(mergeObject, sources,
                        session.getFileSize(), session.getContentType());
            } else {
                minIOUtils.composePrivateObjects(mergeObject, sources);
            }
            if (StringUtils.hasText(session.getFileMd5())) {
                String actualMd5 = minIOUtils.calculatePrivateMd5(mergeObject);
                if (!session.getFileMd5().equalsIgnoreCase(actualMd5)) {
                    minIOUtils.deletePrivateObject(mergeObject);
                    throw new BusinessException("文件完整性校验失败，请重新上传");
                }
                String dedupeLockKey = dedupeLockKey(session.getFileMd5(), session.getFileSize());
                String dedupeLockToken = acquireLock(dedupeLockKey,
                        ContentConstants.UploadRedis.SESSION_LOCK_SECONDS);
                try {
                    if (minIOUtils.privateObjectMatchesSize(targetObject, session.getFileSize())) {
                        minIOUtils.deletePrivateObject(mergeObject);
                    } else {
                        minIOUtils.copyPrivateObject(mergeObject, targetObject);
                    }
                } finally {
                    redisUtils.unlock(dedupeLockKey, dedupeLockToken);
                }
            }
            minIOUtils.deletePrivatePrefix(session.getPrefix());

            session.setObjectKey(targetObject);
            session.setStatus(STATUS_MERGED);
            session.setUploadedChunks(new TreeSet<>());
            saveSession(session);
            redisUtils.del(uploadedChunksKey(session.getUploadId()));
            return toMediaVo(targetObject);
        } finally {
            redisUtils.unlock(lockKey, lockToken);
        }
    }

    @Override
    public void abort(String uploadId, Long userId) {
        abortUploadSession(uploadId, userId, true);
    }

    @Override
    public void bindArticle(String uploadId, Long articleId, Long userId) {
        String lockKey = sessionLockKey(uploadId);
        String lockToken = acquireLock(lockKey, ContentConstants.UploadRedis.SESSION_LOCK_SECONDS);
        try {
            UploadSession session = requireOwnedSession(uploadId, userId);
            session.setArticleId(articleId);
            saveSession(session);
            linkArticle(articleId, uploadId);
        } finally {
            redisUtils.unlock(lockKey, lockToken);
        }
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
                abortUploadSession(uploadId, userId, deleteMerged);
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

    /** 在跨实例会话锁内先标记中止，再清理 MinIO，避免上传完成回写重新激活会话。 */
    private void abortUploadSession(String uploadId, Long userId, boolean deleteMerged) {
        String lockKey = sessionLockKey(uploadId);
        String lockToken = acquireLock(lockKey, ContentConstants.UploadRedis.SESSION_LOCK_SECONDS);
        try {
            UploadSession session = requireOwnedSession(uploadId, userId);
            abortSession(session, deleteMerged);
        } finally {
            redisUtils.unlock(lockKey, lockToken);
        }
    }

    private void abortSession(UploadSession session, boolean deleteMerged) {
        if (STATUS_ABORTED.equals(session.getStatus())) {
            return;
        }
        String sessionKey = sessionKey(session.getUploadId());
        String raw = redisUtils.get(sessionKey);
        session.setStatus(STATUS_ABORTED);
        if (raw != null && !redisUtils.compareAndSet(sessionKey, raw, JSON.toJSONString(session),
                ContentConstants.UploadRedis.SESSION_TTL_SECONDS)) {
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
        removeMd5Index(session);
        redisUtils.del(uploadedChunksKey(session.getUploadId()));
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

    /** 使用 Redis Set 原子记录分片，避免并发上传不同分片时互相覆盖整段 JSON。 */
    private boolean markChunkUploaded(String uploadId, int chunkIndex, Long userId) {
        String key = sessionKey(uploadId);
        String chunksKey = uploadedChunksKey(uploadId);
        for (int attempt = 0; attempt < 8; attempt++) {
            String raw = redisUtils.get(key);
            UploadSession session = parseSession(raw);
            if (session == null || !Objects.equals(session.getUserId(), userId)
                    || !STATUS_UPLOADING.equals(session.getStatus())) {
                return false;
            }
            if (isChunkUploaded(session, chunkIndex)) {
                return true;
            }
            if (redisUtils.addSetMemberIfValueMatches(key, chunksKey, raw, String.valueOf(chunkIndex),
                    ContentConstants.UploadRedis.SESSION_TTL_SECONDS)) {
                if (StringUtils.hasText(session.getFileMd5())) {
                    redisUtils.setEx(md5IndexKey(session.getUserId(), session.getFileMd5()),
                            session.getUploadId(), ContentConstants.UploadRedis.SESSION_TTL_SECONDS);
                }
                return true;
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

    private String acquireLock(String key, long leaseSeconds) {
        String token = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 30; attempt++) {
            if (Boolean.TRUE.equals(redisUtils.setIfAbsent(key, token, leaseSeconds))) {
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

    private String uploadedChunksKey(String uploadId) {
        return sessionKey(uploadId) + ContentConstants.UploadRedis.UPLOADED_CHUNKS_SUFFIX;
    }

    private String sessionLockKey(String uploadId) {
        return ContentConstants.UploadRedis.SESSION_LOCK_PREFIX + uploadId;
    }

    private String initLockKey(Long userId, String fileMd5, Long fileSize) {
        return ContentConstants.UploadRedis.INIT_LOCK_PREFIX + userId + ":" + fileMd5 + ":" + fileSize;
    }

    private String dedupeLockKey(String fileMd5, Long fileSize) {
        return ContentConstants.UploadRedis.DEDUPE_LOCK_PREFIX + fileMd5 + ":" + fileSize;
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
        redisUtils.deleteIfValueMatches(indexKey, session.getUploadId());
    }

    private boolean isChunkUploaded(UploadSession session, int chunkIndex) {
        if (session.getUploadedChunks() != null && session.getUploadedChunks().contains(chunkIndex)) {
            return true;
        }
        return redisUtils.setContains(uploadedChunksKey(session.getUploadId()), String.valueOf(chunkIndex));
    }

    /** 合并兼容旧版 JSON 会话记录和新版 Redis Set，支持滚动发布期间断点续传。 */
    private List<Integer> uploadedChunkIndexes(UploadSession session) {
        TreeSet<Integer> result = new TreeSet<>();
        if (session.getUploadedChunks() != null) {
            result.addAll(session.getUploadedChunks());
        }
        for (String member : redisUtils.setMembers(uploadedChunksKey(session.getUploadId()))) {
            try {
                result.add(Integer.parseInt(member));
            } catch (NumberFormatException e) {
                log.warn("忽略无效的上传分片状态: uploadId={}, value={}", session.getUploadId(), member);
            }
        }
        return new ArrayList<>(result);
    }

    private ChunkUploadInitVO toInitVo(UploadSession session, boolean instant) {
        ChunkUploadInitVO vo = new ChunkUploadInitVO();
        vo.setUploadId(session.getUploadId());
        vo.setObjectKey(session.getObjectKey());
        vo.setChunkSize(session.getChunkSize());
        vo.setTotalChunks(session.getTotalChunks());
        vo.setUploadedChunks(uploadedChunkIndexes(session));
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
        List<Integer> chunks = uploadedChunkIndexes(session);
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
