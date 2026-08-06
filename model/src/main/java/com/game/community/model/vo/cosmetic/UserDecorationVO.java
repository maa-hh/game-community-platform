package com.game.community.model.vo.cosmetic;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserDecorationVO implements Serializable {

    private Long userId;

    private CosmeticEquippedVO avatarFrame;

    private CosmeticEquippedVO commentCard;

    private CosmeticEquippedVO commentFont;

    private CosmeticEquippedVO postCard;

    private CosmeticEquippedVO profileBg;

    private List<ActiveEffectVO> activeEffects;
}
