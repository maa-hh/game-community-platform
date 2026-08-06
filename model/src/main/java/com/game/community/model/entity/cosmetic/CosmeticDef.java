package com.game.community.model.entity.cosmetic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_cosmetic_def")
public class CosmeticDef implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String category;

    private String effectMode;

    private String slot;

    private String previewUrl;

    private String assetJson;

    private String consumableConfig;

    private Integer defaultDurationSeconds;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
