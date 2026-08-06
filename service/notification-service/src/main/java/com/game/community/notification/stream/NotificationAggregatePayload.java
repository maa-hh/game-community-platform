package com.game.community.notification.stream;

import com.game.community.model.vo.notification.NotificationActorVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class NotificationAggregatePayload implements Serializable {

    private List<NotificationActorVO> actors;

    private Integer total;
}
