package com.game.community.model.entity.shop;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_shop_points_ledger")
public class ShopPointsLedger implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long delta;

    private Long balanceAfter;

    private String bizType;

    private String bizRef;

    private String remark;

    private LocalDateTime createTime;
}
