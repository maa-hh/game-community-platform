package com.game.community.model.vo.gameaccount;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class GameAccountAssetsVO implements Serializable {

    private List<OwnedCharacterVO> characters;

    private List<OwnedSkinVO> skins;

    private List<OwnedItemVO> items;
}
