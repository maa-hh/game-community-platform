package com.game.community.model.dto.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class AddGameReviewReplyDTO implements Serializable {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 1000, message = "回复内容不能超过1000字")
    private String content;

    /** 回复某条已有评价回复时传入其公开 ID。 */
    private String replyToReplyId;
}
