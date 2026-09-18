package com.game.community.model.dto.aiagent;

import com.game.community.model.enums.aiagent.ModerationContentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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

    /** TEXT/ARTICLE 的正文内容，由服务端再按 Token 上限校验。 */
    private String content;

    /** IMAGE 类型的远程图片地址，仅允许服务端配置的白名单 Host。 */
    private String imageUrl;

    /** IMAGE 类型的 Base64 图片内容，服务端限制解码后的最大字节数。 */
    private String imageBase64;

    /** 图片 MIME 类型，无法识别时由服务端回退为 JPEG。 */
    private String mimeType;

    /** 可选模型供应商；空值使用服务端默认配置。 */
    private String provider;

    /** ARTICLE 类型使用的标题；TEXT 类型忽略。 */
    private String title;

    /** ARTICLE 类型使用的全部图片，Agent 对文章只发起一次模型调用。 */
    @Valid
    @Size(max = 20, message = "文章图片数量不能超过 20 张")
    private List<ModerationImageRequest> images;
}
