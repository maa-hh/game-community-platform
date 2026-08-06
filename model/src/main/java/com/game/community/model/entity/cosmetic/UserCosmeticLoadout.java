package com.game.community.model.entity.cosmetic;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_user_cosmetic_loadout")
public class UserCosmeticLoadout implements Serializable {

    @TableId
    private Long userId;

    private String avatarFrameCode;

    private String commentCardCode;

    private String commentFontCode;

    private String postCardCode;

    private String profileBgCode;

    private LocalDateTime updateTime;
}
