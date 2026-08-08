package com.game.community.model.vo.danmaku;

import lombok.Data;

import java.io.Serializable;

@Data
public class DanmakuVO implements Serializable {

    private Long id;

    private String eventId;

    private String clientMessageId;

    private String videoPublicId;

    private Long videoTimeMs;

    private Long displayTimeMs;

    private Long seq;

    private Long accountId;

    private String username;

    private String avatar;

    private String content;

    private Integer status;
}
