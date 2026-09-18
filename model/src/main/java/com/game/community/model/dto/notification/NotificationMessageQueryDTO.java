package com.game.community.model.dto.notification;

import lombok.Data;

/**
 * 通知列表查询参数；只承载公开查询条件，分页边界由通知服务统一归一化。
 */
@Data
public class NotificationMessageQueryDTO {

    private Long page;

    private Long size;

    private Integer eventType;

    private String category;
}
