package com.game.community.notification.sse;

import com.game.community.model.vo.notification.NotificationSseEventVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSseBroadcast implements Serializable {

    private Long userId;

    private String eventName;

    private NotificationSseEventVO payload;
}
