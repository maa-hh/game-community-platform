package com.game.community.model.dto.aiagent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddKnowledgeTextRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题不能超过 128 字")
    private String title;

    @NotBlank(message = "知识内容不能为空")
    @Size(max = 50000, message = "知识内容不能超过 50000 字")
    private String content;

    @Size(max = 255, message = "来源名称不能超过 255 字")
    private String sourceName;
}
