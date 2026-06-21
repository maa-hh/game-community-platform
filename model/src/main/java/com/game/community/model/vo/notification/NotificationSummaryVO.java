package com.game.community.model.vo.notification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSummaryVO implements Serializable {

    private Long unreadNotificationCount;

    private Boolean feedUnread;
}
