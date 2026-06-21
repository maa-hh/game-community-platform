package com.game.community.game.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.model.entity.gameaccount.GameCharacter;
import com.game.community.model.entity.gameaccount.GameItem;
import com.game.community.model.entity.gameaccount.GameSkin;
import com.game.community.model.entity.gameaccount.SignInReward;
import com.game.community.model.mongo.CharacterDetail;
import com.game.community.model.mongo.ItemDetail;
import com.game.community.model.mongo.SkinDetail;

import java.util.List;

public interface GameResourceAdminService {

    Page<GameCharacter> pageCharacters(int page, int size, String keyword, Integer rarity, Integer status);

    Page<GameSkin> pageSkins(int page, int size, String keyword, Long characterId, Integer rarity, Integer status);

    Page<GameItem> pageItems(int page, int size, String keyword, Integer itemType, Integer status);

    void createCharacter(GameCharacter character);

    void updateCharacter(GameCharacter character);

    void disableCharacter(Long id);

    void createSkin(GameSkin skin);

    void updateSkin(GameSkin skin);

    void disableSkin(Long id);

    void createItem(GameItem item);

    void updateItem(GameItem item);

    void disableItem(Long id);

    List<SignInReward> listSignInRewards();

    void createSignInReward(SignInReward reward);

    void updateSignInReward(SignInReward reward);

    void deleteSignInReward(Long id);

    CharacterDetail getCharacterDetail(String characterCode);

    void saveCharacterDetail(CharacterDetail detail);

    SkinDetail getSkinDetail(String skinCode);

    void saveSkinDetail(SkinDetail detail);

    ItemDetail getItemDetail(String itemCode);

    void saveItemDetail(ItemDetail detail);
}
