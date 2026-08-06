package com.game.community.content.service;

import com.game.community.model.base.Result;
import com.game.community.model.dto.file.ChunkUploadAbortDTO;
import com.game.community.model.dto.file.ChunkUploadBindDTO;
import com.game.community.model.dto.file.ChunkUploadInitDTO;
import com.game.community.model.dto.file.ChunkUploadMergeDTO;
import com.game.community.model.vo.file.ChunkUploadInitVO;
import com.game.community.model.vo.file.ChunkUploadStatusVO;
import com.game.community.model.vo.file.MediaUploadVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传 HTTP API（图片直传 + 分片上传门面）
 */
public interface FileUploadService {

    Result<List<MediaUploadVO>> uploadImages(List<MultipartFile> files);

    Result<ChunkUploadInitVO> initUpload(ChunkUploadInitDTO dto);

    Result<ChunkUploadStatusVO> uploadChunk(String uploadId, Integer chunkIndex, MultipartFile file);

    Result<MediaUploadVO> mergeUpload(ChunkUploadMergeDTO dto);

    Result<Void> bindUpload(ChunkUploadBindDTO dto);

    Result<Void> abortUpload(ChunkUploadAbortDTO dto);

    Result<ChunkUploadStatusVO> uploadStatus(String uploadId);
}
