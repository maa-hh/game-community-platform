package com.game.community.model.vo.file;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ChunkUploadStatusVO implements Serializable {

    private String uploadId;
    private String status;
    private int totalChunks;
    private int uploadedCount;
    private List<Integer> uploadedChunks;
    private Integer percent;
    private String pendingUrl;
    private String previewUrl;
}
