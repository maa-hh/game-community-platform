package com.game.community.model.entity.gameaccount;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_game_delivery_record")
public class GameDeliveryRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long gameAccountId;

    private Integer productType;

    private String businessCode;

    private Long businessId;

    private Integer quantity;

    private Integer status;

    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
