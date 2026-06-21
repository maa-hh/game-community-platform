package com.game.community.game.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.vo.gameaccount.CharacterResourceVO;
import com.game.community.model.vo.gameaccount.ItemResourceVO;
import com.game.community.model.vo.gameaccount.OwnedCharacterVO;
import com.game.community.model.vo.gameaccount.OwnedItemVO;
import com.game.community.model.vo.gameaccount.OwnedSkinVO;
import com.game.community.model.vo.gameaccount.SkinResourceVO;

public interface GameAssetService {

    PageResult<OwnedCharacterVO> listOwnedCharacters(Long userId, int page, int size);

    PageResult<OwnedSkinVO> listOwnedSkins(Long userId, int page, int size);

    PageResult<OwnedItemVO> listOwnedItems(Long userId, int page, int size);

    PageResult<CharacterResourceVO> listCharacterCatalog(Long userId, int page, int size, String keyword, Integer rarity);

    PageResult<SkinResourceVO> listSkinCatalog(Long userId, int page, int size, String keyword, Long characterId, Integer rarity);

    PageResult<ItemResourceVO> listItemCatalog(Long userId, int page, int size, String keyword, Integer itemType);
}
