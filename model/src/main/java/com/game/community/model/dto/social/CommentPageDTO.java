package com.game.community.model.dto.social;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class CommentPageDTO implements Serializable {

    @NotNull(message = "文章ID不能为空")
    /** 文章对外标识；内部主键由 Service 边界解析。 */
    private String articleId;

    private Long page = 1L;

    private Long size = 20L;
}
