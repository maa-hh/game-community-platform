package com.game.community.model.dto.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class ChunkUploadBindDTO implements Serializable {

    @NotBlank(message = "uploadId 不能为空")
    private String uploadId;

    @NotBlank(message = "articleId 不能为空")
    private String articleId;
}
