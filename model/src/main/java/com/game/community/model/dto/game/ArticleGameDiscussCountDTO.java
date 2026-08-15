package com.game.community.model.dto.game;

import lombok.Data;

import java.io.Serializable;

/**
 * 文章与游戏关联表的聚合查询结果，仅供内容服务内部映射使用。
 */
@Data
public class ArticleGameDiscussCountDTO implements Serializable {

    private Long appId;

    private Long discussCount;
}
