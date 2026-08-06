package com.game.community.content.service;

import com.game.community.model.dto.file.ChunkUploadInitDTO;
import com.game.community.model.vo.file.ChunkUploadInitVO;
import com.game.community.model.vo.file.ChunkUploadStatusVO;
import com.game.community.model.vo.file.MediaUploadVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 大文件分片上传（私有桶）
 */
public interface ChunkUploadService {

    ChunkUploadInitVO init(ChunkUploadInitDTO dto, Long userId);

    ChunkUploadStatusVO uploadChunk(String uploadId, int chunkIndex, MultipartFile file, Long userId);

    MediaUploadVO merge(String uploadId, Long userId);

    void abort(String uploadId, Long userId);

    /**
     * 绑定上传会话到文章（取消上架/删除时可统一中止）
     */
    void bindArticle(String uploadId, Long articleId, Long userId);

    /**
     * 中止文章关联的上传。
     *
     * @param deleteMerged false=取消上架：停传并清未合并分片，保留已合并文件；
     *                     true=删除：连已合并对象一并删除
     */
    void abortByArticleId(Long articleId, Long userId, boolean deleteMerged);

    ChunkUploadStatusVO status(String uploadId, Long userId);

    List<ChunkUploadStatusVO> listByArticleId(Long articleId, Long userId);
}
