package com.game.community.model.entity.gameaccount;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_sign_in_reward")
public class SignInReward implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer dayIndex;

    private Integer rewardType;

    private String businessCode;

    private String rewardCode;

    private Integer quantity;

    private String rewardName;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
