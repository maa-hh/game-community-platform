package com.game.community.model.dto.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class AddReplyDTO implements Serializable {

    @NotNull(message = "评论ID不能为空")
    private Long commentId;

    private Long replyToAccountId;

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 1000, message = "回复内容不能超过1000字")
    private String content;
}
