package com.game.community.model.entity.gameaccount;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_user_game_bind")
public class UserGameBind implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long gameAccountId;

    private LocalDateTime bindTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
