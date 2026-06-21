package com.game.community.model.vo.notification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSseEventVO implements Serializable {

    private String eventType;

    private NotificationSummaryVO summary;

    private NotificationMessageVO message;
}
