package com.game.community.content.service.impl;

import com.game.community.common.constant.content.ContentConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.content.service.ChunkUploadService;
import com.game.community.content.service.ArticleService;
import com.game.community.content.service.FileUploadService;
import com.game.community.model.base.Result;
import com.game.community.model.dto.file.ChunkUploadAbortDTO;
import com.game.community.model.dto.file.ChunkUploadBindDTO;
import com.game.community.model.dto.file.ChunkUploadInitDTO;
import com.game.community.model.dto.file.ChunkUploadMergeDTO;
import com.game.community.model.vo.file.ChunkUploadInitVO;
import com.game.community.model.vo.file.ChunkUploadStatusVO;
import com.game.community.model.vo.file.MediaUploadVO;
import com.game.community.utils.MinIOUtils;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {

    private static final String PENDING_URL_PREFIX = "pending://";

    private final MinIOUtils minIOUtils;
    private final ChunkUploadService chunkUploadService;
    private final ArticleService articleService;
    private final Executor fileUploadExecutor;

    public FileUploadServiceImpl(MinIOUtils minIOUtils,
                                 ChunkUploadService chunkUploadService,
                                 ArticleService articleService,
                                 @Qualifier("contentFileUploadExecutor") Executor fileUploadExecutor) {
        this.minIOUtils = minIOUtils;
        this.chunkUploadService = chunkUploadService;
        this.articleService = articleService;
        this.fileUploadExecutor = fileUploadExecutor;
    }

    @Override
    public Result<List<MediaUploadVO>> uploadImages(List<MultipartFile> files) {
        validateArticleImages(files);
        List<CompletableFuture<MediaUploadVO>> uploads = files.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> uploadImage(file), fileUploadExecutor))
                .toList();
        try {
            CompletableFuture.allOf(uploads.toArray(new CompletableFuture[0])).join();
            return Result.success(uploads.stream().map(CompletableFuture::join).toList());
        } catch (CompletionException exception) {
            cleanupUploadedImages(uploads);
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new BusinessException("图片上传失败");
        }
    }

    /** 单张图片写入私有桶并组装预览协议。 */
    private MediaUploadVO uploadImage(MultipartFile file) {
        String fileMd5 = minIOUtils.calculateMd5(file);
        String objectKey = globalObjectKey(fileMd5, file.getSize());
        if (minIOUtils.privateObjectExists(objectKey)) {
            return toMediaVo(objectKey);
        }

        try {
            minIOUtils.uploadPrivateFile(file, objectKey);
            return toMediaVo(objectKey);
        } catch (RuntimeException exception) {
            if (!isGlobalObject(objectKey)) {
                try {
                    minIOUtils.deletePrivateObject(objectKey);
                } catch (RuntimeException cleanupException) {
                    log.warn("生成图片预览地址失败后的对象清理失败: objectKey={}",
                            objectKey, cleanupException);
                }
            }
            throw exception;
        }
    }

    private MediaUploadVO toMediaVo(String objectKey) {
        MediaUploadVO vo = new MediaUploadVO();
        vo.setObjectKey(objectKey);
        vo.setPendingUrl(PENDING_URL_PREFIX + objectKey);
        vo.setPreviewUrl(minIOUtils.generatePrivateUrl(objectKey,
                ContentConstants.MediaLimit.PRESIGNED_EXPIRE_SECONDS));
        return vo;
    }

    /** 多图批量上传部分失败时删除本批次已写入对象，避免产生孤儿文件。 */
    private void cleanupUploadedImages(List<CompletableFuture<MediaUploadVO>> uploads) {
        uploads.stream()
                .filter(CompletableFuture::isDone)
                .filter(upload -> !upload.isCompletedExceptionally())
                .map(CompletableFuture::join)
                .filter(media -> !isGlobalObject(media.getObjectKey()))
                .forEach(media -> {
                    try {
                        minIOUtils.deletePrivateObject(media.getObjectKey());
                    } catch (RuntimeException cleanupException) {
                        log.warn("批量图片上传失败后的对象清理失败: objectKey={}",
                                media.getObjectKey(), cleanupException);
                    }
                });
    }

    private boolean isGlobalObject(String objectKey) {
        return StringUtils.hasText(objectKey)
                && objectKey.startsWith(ContentConstants.UploadRedis.GLOBAL_OBJECT_PREFIX);
    }

    private String globalObjectKey(String fileMd5, long fileSize) {
        return ContentConstants.UploadRedis.GLOBAL_OBJECT_PREFIX + fileMd5 + "-" + fileSize;
    }

    @Override
    public Result<ChunkUploadInitVO> initUpload(ChunkUploadInitDTO dto) {
        return Result.success(chunkUploadService.init(dto, requireUserId()));
    }

    @Override
    public Result<ChunkUploadStatusVO> uploadChunk(String uploadId, Integer chunkIndex, MultipartFile file) {
        if (chunkIndex == null) {
            throw new BusinessException("chunkIndex 不能为空");
        }
        return Result.success(chunkUploadService.uploadChunk(uploadId, chunkIndex, file, requireUserId()));
    }

    @Override
    public Result<MediaUploadVO> mergeUpload(ChunkUploadMergeDTO dto) {
        return Result.success(chunkUploadService.merge(dto.getUploadId(), requireUserId()));
    }

    @Override
    public Result<Void> bindUpload(ChunkUploadBindDTO dto) {
        Long articleId = articleService.resolvePublicId(dto.getArticleId());
        chunkUploadService.bindArticle(dto.getUploadId(), articleId, requireUserId());
        return Result.success(null);
    }

    @Override
    public Result<Void> abortUpload(ChunkUploadAbortDTO dto) {
        chunkUploadService.abort(dto.getUploadId(), requireUserId());
        return Result.success(null);
    }

    @Override
    public Result<ChunkUploadStatusVO> uploadStatus(String uploadId) {
        return Result.success(chunkUploadService.status(uploadId, requireUserId()));
    }

    private Long requireUserId() {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        return userId;
    }

    private void validateArticleImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (files.size() > ContentConstants.MediaLimit.IMAGE_MAX_COUNT) {
            throw new BusinessException("文章图片最多支持"
                    + ContentConstants.MediaLimit.IMAGE_MAX_COUNT + "张");
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new BusinessException("图片不能为空");
            }
            if (file.getSize() > ContentConstants.MediaLimit.IMAGE_MAX_BYTES) {
                throw new BusinessException("单张图片大小不能超过5MB");
            }
            String contentType = file.getContentType();
            if (!StringUtils.hasText(contentType) || !contentType.startsWith("image/")) {
                throw new BusinessException("仅支持上传图片文件");
            }
            String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
            if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                    || name.endsWith(".webp") || name.endsWith(".gif"))) {
                throw new BusinessException("图片仅支持 jpg/png/webp/gif");
            }
        }
    }
}
