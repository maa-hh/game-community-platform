package com.game.community.content.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.content.service.FileUploadService;
import com.game.community.model.base.Result;
import com.game.community.model.dto.file.ChunkUploadAbortDTO;
import com.game.community.model.dto.file.ChunkUploadBindDTO;
import com.game.community.model.dto.file.ChunkUploadInitDTO;
import com.game.community.model.dto.file.ChunkUploadMergeDTO;
import com.game.community.model.vo.file.ChunkUploadInitVO;
import com.game.community.model.vo.file.ChunkUploadStatusVO;
import com.game.community.model.vo.file.MediaUploadVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传：图片直传私有桶；视频走分片会话。
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileUploadService fileUploadService;

    @LoginCheck
    @PostMapping("/upload")
    public Result<List<MediaUploadVO>> upload(@RequestParam("files") List<MultipartFile> files) {
        return fileUploadService.uploadImages(files);
    }

    @LoginCheck
    @PostMapping("/upload/init")
    public Result<ChunkUploadInitVO> initUpload(@Valid @RequestBody ChunkUploadInitDTO dto) {
        return fileUploadService.initUpload(dto);
    }

    @LoginCheck
    @PostMapping("/upload/chunk")
    public Result<ChunkUploadStatusVO> uploadChunk(@RequestParam("uploadId") String uploadId,
                                                   @RequestParam("chunkIndex") Integer chunkIndex,
                                                   @RequestParam("file") MultipartFile file) {
        return fileUploadService.uploadChunk(uploadId, chunkIndex, file);
    }

    @LoginCheck
    @PostMapping("/upload/merge")
    public Result<MediaUploadVO> mergeUpload(@Valid @RequestBody ChunkUploadMergeDTO dto) {
        return fileUploadService.mergeUpload(dto);
    }

    @LoginCheck
    @PostMapping("/upload/bind")
    public Result<Void> bindUpload(@Valid @RequestBody ChunkUploadBindDTO dto) {
        return fileUploadService.bindUpload(dto);
    }

    @LoginCheck
    @PostMapping("/upload/abort")
    public Result<Void> abortUpload(@Valid @RequestBody ChunkUploadAbortDTO dto) {
        return fileUploadService.abortUpload(dto);
    }

    @LoginCheck
    @GetMapping("/upload/{uploadId}/status")
    public Result<ChunkUploadStatusVO> uploadStatus(@PathVariable("uploadId") String uploadId) {
        return fileUploadService.uploadStatus(uploadId);
    }
}
