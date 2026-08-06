package com.game.community.model.dto.file;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 大文件分片上传初始化
 */
@Data
public class ChunkUploadInitDTO implements Serializable {

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件大小无效")
    private Long fileSize;

    /** 可选：文件 MD5，用于断点续传/秒传 */
    private String fileMd5;

    private String contentType;

    /**
     * 业务类型：video / cover / image，默认 video
     */
    private String bizType;

    /** 关联草稿文章 ID（可选，删帖时可一并清理） */
    private Long articleId;
}
