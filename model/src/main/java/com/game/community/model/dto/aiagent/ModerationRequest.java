package com.game.community.model.dto.aiagent;

import com.game.community.model.enums.aiagent.ModerationContentType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * AI Agent 内部审核命令。
 * 文本使用 content；图片优先使用 imageUrl，也可传 imageBase64 + mimeType。
 */
@Data
public class ModerationRequest {

    @NotNull(message = "审核类型不能为空")
    private ModerationContentType type;

    private String content;

    private String imageUrl;

    private String imageBase64;

    private String mimeType;

    /** 可选模型供应商；空值使用服务端默认配置。 */
    private String provider;

    /** ARTICLE 类型使用的标题；TEXT 类型忽略。 */
    private String title;

    /** ARTICLE 类型使用的全部图片，Agent 对文章只发起一次模型调用。 */
    private List<ModerationImageRequest> images;
}
