package com.game.community.model.dto.file;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

@Data
public class ChunkUploadMergeDTO implements Serializable {

    @NotBlank(message = "uploadId 不能为空")
    private String uploadId;
}
