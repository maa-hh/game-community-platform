package com.game.community.model.vo.notification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSseEventVO implements Serializable {

    private String eventType;

    private NotificationSummaryVO summary;

    private NotificationMessageVO message;

    /** 个人主页数据失效域；仅 profile_invalidated 事件使用。 */
    private String eventId;

    private List<String> invalidationDomains;

    public NotificationSseEventVO(String eventType, NotificationSummaryVO summary,
                                  NotificationMessageVO message) {
        this(eventType, summary, message, null, null);
    }
}
