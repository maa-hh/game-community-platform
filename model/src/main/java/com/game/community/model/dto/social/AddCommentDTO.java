package com.game.community.model.dto.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;

@Data
public class AddCommentDTO implements Serializable {

    @NotNull(message = "文章ID不能为空")
    private String articleId;

    /** 控制器解析后的内部主键，仅供社交服务内部使用。 */
    @JsonIgnore
    private Long internalArticleId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容不能超过2000字")
    private String content;
}
