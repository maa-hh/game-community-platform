package com.game.community.danmaku.service;

import com.game.community.model.entity.danmaku.DanmakuMessage;
import com.game.community.model.message.DanmakuEvent;
import com.game.community.model.vo.danmaku.DanmakuVO;
import org.springframework.stereotype.Component;

/** 统一内部弹幕事件、持久化实体与公开响应之间的字段映射。 */
@Component
public class DanmakuViewMapper {

    public DanmakuVO fromEvent(DanmakuEvent event) {
        DanmakuVO vo = new DanmakuVO();
        vo.setId(event.getId());
        vo.setEventId(event.getEventId());
        vo.setClientMessageId(event.getClientMessageId());
        vo.setVideoPublicId(event.getVideoPublicId());
        vo.setVideoTimeMs(event.getVideoTimeMs());
        vo.setDisplayTimeMs(event.getDisplayTimeMs());
        vo.setSeq(event.getSeq());
        vo.setAccountId(event.getAccountId());
        vo.setUsername(event.getUsernameSnapshot());
        vo.setAvatar(event.getAvatarSnapshot());
        vo.setContent(event.getContent());
        vo.setStatus(event.getStatus());
        return vo;
    }

    public DanmakuVO fromEntity(DanmakuMessage entity) {
        DanmakuVO vo = new DanmakuVO();
        vo.setId(entity.getId());
        vo.setEventId(entity.getEventId());
        vo.setClientMessageId(entity.getClientMessageId());
        vo.setVideoPublicId(entity.getVideoPublicId());
        vo.setVideoTimeMs(entity.getVideoTimeMs());
        vo.setDisplayTimeMs(entity.getDisplayTimeMs());
        vo.setSeq(entity.getSeq());
        vo.setAccountId(entity.getAccountId());
        vo.setUsername(entity.getUsernameSnapshot());
        vo.setAvatar(entity.getAvatarSnapshot());
        vo.setContent(entity.getContent());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
