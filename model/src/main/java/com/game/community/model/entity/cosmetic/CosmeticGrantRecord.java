package com.game.community.model.entity.cosmetic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_cosmetic_grant_record")
public class CosmeticGrantRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private String cosmeticCode;

    private Integer quantity;

    private LocalDateTime createTime;
}
