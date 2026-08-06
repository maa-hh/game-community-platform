package com.game.community.model.vo.notification;

import lombok.Data;

import java.io.Serializable;

@Data
public class NotificationActorVO implements Serializable {

    private Long accountId;

    private String username;

    private String avatar;

    /** like / favorite */
    private String action;
}
