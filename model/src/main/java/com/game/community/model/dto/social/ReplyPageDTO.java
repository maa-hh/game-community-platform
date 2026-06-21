package com.game.community.model.dto.social;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class ReplyPageDTO implements Serializable {

    @NotNull(message = "评论ID不能为空")
    private Long commentId;

    private Long page = 1L;

    private Long size = 20L;
}
