package com.game.community.model.entity.game;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_game_chart_snapshot")
public class GameChartSnapshot implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** hot / new / free / discount */
    private String boardType;

    /** yyyy-Www */
    private String periodKey;

    private Long appId;

    private Integer rankNo;

    private LocalDateTime snapshotTime;

    private String snapshotId;

    private Integer isCurrent;
}
