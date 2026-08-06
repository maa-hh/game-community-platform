package com.game.community.model.vo.notification;

import lombok.Data;

import java.io.Serializable;

@Data
public class NotificationCategorySummaryVO implements Serializable {

    private String category;

    private Long unreadCount;
}
