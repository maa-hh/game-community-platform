package com.game.community.model.dto.aiagent;

import lombok.Data;

/** 文章批量审核中的图片载荷；优先传 Base64，无法读取时传 URL。 */
@Data
public class ModerationImageRequest {

    private String imageUrl;

    private String imageBase64;

    private String mimeType;
}
