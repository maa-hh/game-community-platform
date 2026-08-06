package com.game.community.danmaku.service;

import com.game.community.model.vo.danmaku.DanmakuVO;
import lombok.Data;

@Data
public class DanmakuRealtimeMessage {

    private String type;

    private DanmakuVO data;

    private Long messageId;

    public static DanmakuRealtimeMessage created(DanmakuVO data) {
        DanmakuRealtimeMessage message = new DanmakuRealtimeMessage();
        message.type = "danmaku";
        message.data = data;
        return message;
    }

    public static DanmakuRealtimeMessage removed(Long messageId) {
        DanmakuRealtimeMessage message = new DanmakuRealtimeMessage();
        message.type = "danmaku_removed";
        message.messageId = messageId;
        return message;
    }
}
