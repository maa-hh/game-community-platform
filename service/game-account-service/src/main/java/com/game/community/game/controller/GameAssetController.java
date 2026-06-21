package com.game.community.game.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.game.service.GameAssetService;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.vo.gameaccount.CharacterResourceVO;
import com.game.community.model.vo.gameaccount.ItemResourceVO;
import com.game.community.model.vo.gameaccount.OwnedCharacterVO;
import com.game.community.model.vo.gameaccount.OwnedItemVO;
import com.game.community.model.vo.gameaccount.OwnedSkinVO;
import com.game.community.model.vo.gameaccount.SkinResourceVO;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/game-account")
@RequiredArgsConstructor
public class GameAssetController {

    private final GameAssetService gameAssetService;

    @LoginCheck
    @GetMapping("/assets/characters")
    public Result<PageResult<OwnedCharacterVO>> listOwnedCharacters(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size) {
        return Result.success(gameAssetService.listOwnedCharacters(UserThreadLocal.getUserId(), page, size));
    }

    @LoginCheck
    @GetMapping("/assets/skins")
    public Result<PageResult<OwnedSkinVO>> listOwnedSkins(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size) {
        return Result.success(gameAssetService.listOwnedSkins(UserThreadLocal.getUserId(), page, size));
    }

    @LoginCheck
    @GetMapping("/assets/items")
    public Result<PageResult<OwnedItemVO>> listOwnedItems(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size) {
        return Result.success(gameAssetService.listOwnedItems(UserThreadLocal.getUserId(), page, size));
    }

    @LoginCheck
    @GetMapping("/resources/characters")
    public Result<PageResult<CharacterResourceVO>> listCharacterCatalog(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "rarity", required = false) Integer rarity) {
        return Result.success(gameAssetService.listCharacterCatalog(UserThreadLocal.getUserId(), page, size, keyword, rarity));
    }

    @LoginCheck
    @GetMapping("/resources/skins")
    public Result<PageResult<SkinResourceVO>> listSkinCatalog(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "characterId", required = false) Long characterId,
            @RequestParam(name = "rarity", required = false) Integer rarity) {
        return Result.success(gameAssetService.listSkinCatalog(UserThreadLocal.getUserId(), page, size, keyword, characterId, rarity));
    }

    @LoginCheck
    @GetMapping("/resources/items")
    public Result<PageResult<ItemResourceVO>> listItemCatalog(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "itemType", required = false) Integer itemType) {
        return Result.success(gameAssetService.listItemCatalog(UserThreadLocal.getUserId(), page, size, keyword, itemType));
    }
}
