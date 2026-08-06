package com.game.community.model.entity.game;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_game_review")
public class GameReview implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appId;

    private Long userId;

    private Integer score;

    private String content;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
