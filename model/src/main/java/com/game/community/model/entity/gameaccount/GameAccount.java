package com.game.community.model.entity.gameaccount;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_game_account")
public class GameAccount implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String accountNo;

    private String name;

    private Integer level;

    private Long gold;

    private Long diamond;

    private String currentSeasonRank;

    private String historySeasonRank;

    private Integer status;

    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
