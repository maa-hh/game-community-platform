package com.game.community.model.entity.game;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 游戏搜索索引可靠投递 Outbox，保证目录事务提交后索引事件最终可投递。 */
@Data
@TableName("t_game_search_outbox")
public class GameSearchOutboxEvent implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventKey;

    private Long appId;

    private String action;

    private String payload;

    /** 0待发送，1发送中，2已发送，3重试等待，4死信。 */
    private Integer status;

    private Integer retryCount;

    private LocalDateTime nextRetryTime;

    private String lockToken;

    private LocalDateTime lockTime;

    private String lastError;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
