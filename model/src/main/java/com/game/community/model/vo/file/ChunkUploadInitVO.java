package com.game.community.model.vo.file;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ChunkUploadInitVO implements Serializable {

    private String uploadId;
    /** 已合并文件的私有对象 key（仅秒传/恢复已完成会话时返回） */
    private String objectKey;
    private long chunkSize;
    private int totalChunks;
    /** 已上传分片下标（断点续传） */
    private List<Integer> uploadedChunks;
    /** 秒传：已有可用对象时直接返回 */
    private boolean instant;
    private String pendingUrl;
    private String previewUrl;
}
