package com.game.community.model.entity.recommend;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 热榜历史快照（Top100 行）
 */
@Data
@TableName("t_hot_rank_snapshot")
public class HotRankSnapshot implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String boardType;

    private String periodKey;

    private String categoryScope;

    private Long articleId;

    private Integer rankNo;

    private Double hotScore;

    private LocalDateTime snapshotTime;
}
