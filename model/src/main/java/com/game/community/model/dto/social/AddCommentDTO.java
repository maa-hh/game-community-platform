package com.game.community.model.dto.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class AddCommentDTO implements Serializable {

    /** 对外文章标识，Service 内部才解析为数据库主键。 */
    @NotNull(message = "文章ID不能为空")
    private String articleId;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容不能超过2000字")
    private String content;
}
