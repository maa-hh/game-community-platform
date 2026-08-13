package com.game.community.model.message;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Kafka 可靠事件；同一事件可被重复消费，落库按 eventId 幂等。 */
@Data
public class DanmakuEvent implements Serializable {

    private Long id;

    private String eventId;

    /** 发送时已解析出的内部文章 ID；历史消息为空时由消费方按 videoPublicId 回源解析。 */
    private Long articleId;

    private String clientMessageId;

    private String videoPublicId;

    private Long videoTimeMs;

    private Long displayTimeMs;

    private Long seq;

    private Long accountId;

    private String usernameSnapshot;

    private String avatarSnapshot;

    private String content;

    private Integer status;

    private LocalDateTime eventTime;
}
