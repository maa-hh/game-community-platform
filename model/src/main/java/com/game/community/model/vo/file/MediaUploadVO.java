package com.game.community.model.vo.file;

import lombok.Data;

import java.io.Serializable;

/**
 * 媒体上传结果：落库用 pendingUrl，预览用 previewUrl
 */
@Data
public class MediaUploadVO implements Serializable {

    /** 私有桶 objectKey */
    private String objectKey;

    /** 落库协议：pending://objectKey */
    private String pendingUrl;

    /** 作者短时预览地址 */
    private String previewUrl;
}
