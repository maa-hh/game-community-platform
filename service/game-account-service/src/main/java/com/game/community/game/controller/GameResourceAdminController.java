package com.game.community.game.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.game.service.GameResourceAdminService;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.entity.gameaccount.GameCharacter;
import com.game.community.model.entity.gameaccount.GameItem;
import com.game.community.model.entity.gameaccount.GameSkin;
import com.game.community.model.entity.gameaccount.SignInReward;
import com.game.community.model.mongo.CharacterDetail;
import com.game.community.model.mongo.ItemDetail;
import com.game.community.model.mongo.SkinDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/game-account")
@RequiredArgsConstructor
public class GameResourceAdminController {

    private final GameResourceAdminService gameResourceAdminService;

    @LoginCheck
    @GetMapping("/resources/characters/{characterCode}/detail")
    public Result<CharacterDetail> getCharacterDetail(@PathVariable("characterCode") String characterCode) {
        return Result.success(gameResourceAdminService.getCharacterDetail(characterCode));
    }

    @LoginCheck
    @GetMapping("/resources/skins/{skinCode}/detail")
    public Result<SkinDetail> getSkinDetail(@PathVariable("skinCode") String skinCode) {
        return Result.success(gameResourceAdminService.getSkinDetail(skinCode));
    }

    @LoginCheck
    @GetMapping("/resources/items/{itemCode}/detail")
    public Result<ItemDetail> getItemDetail(@PathVariable("itemCode") String itemCode) {
        return Result.success(gameResourceAdminService.getItemDetail(itemCode));
    }

    @AdminCheck
    @GetMapping("/admin/characters")
    public Result<PageResult<GameCharacter>> pageCharacters(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "rarity", required = false) Integer rarity,
            @RequestParam(name = "status", required = false) Integer status) {
        Page<GameCharacter> result = gameResourceAdminService.pageCharacters(page, size, keyword, rarity, status);
        return Result.success(PageResult.of(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal()));
    }

    @AdminCheck
    @PostMapping("/admin/characters")
    public Result<Void> createCharacter(@RequestBody GameCharacter character) {
        gameResourceAdminService.createCharacter(character);
        return Result.success(null);
    }

    @AdminCheck
    @PutMapping("/admin/characters")
    public Result<Void> updateCharacter(@RequestBody GameCharacter character) {
        gameResourceAdminService.updateCharacter(character);
        return Result.success(null);
    }

    @AdminCheck
    @DeleteMapping("/admin/characters/{id}")
    public Result<Void> disableCharacter(@PathVariable("id") Long id) {
        gameResourceAdminService.disableCharacter(id);
        return Result.success(null);
    }

    @AdminCheck
    @PostMapping("/admin/characters/detail")
    public Result<Void> saveCharacterDetail(@RequestBody CharacterDetail detail) {
        gameResourceAdminService.saveCharacterDetail(detail);
        return Result.success(null);
    }

    @AdminCheck
    @GetMapping("/admin/skins")
    public Result<PageResult<GameSkin>> pageSkins(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "characterId", required = false) Long characterId,
            @RequestParam(name = "rarity", required = false) Integer rarity,
            @RequestParam(name = "status", required = false) Integer status) {
        Page<GameSkin> result = gameResourceAdminService.pageSkins(page, size, keyword, characterId, rarity, status);
        return Result.success(PageResult.of(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal()));
    }

    @AdminCheck
    @PostMapping("/admin/skins")
    public Result<Void> createSkin(@RequestBody GameSkin skin) {
        gameResourceAdminService.createSkin(skin);
        return Result.success(null);
    }

    @AdminCheck
    @PutMapping("/admin/skins")
    public Result<Void> updateSkin(@RequestBody GameSkin skin) {
        gameResourceAdminService.updateSkin(skin);
        return Result.success(null);
    }

    @AdminCheck
    @DeleteMapping("/admin/skins/{id}")
    public Result<Void> disableSkin(@PathVariable("id") Long id) {
        gameResourceAdminService.disableSkin(id);
        return Result.success(null);
    }

    @AdminCheck
    @PostMapping("/admin/skins/detail")
    public Result<Void> saveSkinDetail(@RequestBody SkinDetail detail) {
        gameResourceAdminService.saveSkinDetail(detail);
        return Result.success(null);
    }

    @AdminCheck
    @GetMapping("/admin/items")
    public Result<PageResult<GameItem>> pageItems(
            @RequestParam(name = "page", defaultValue = "1") Integer page,
            @RequestParam(name = "size", defaultValue = "10") Integer size,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "itemType", required = false) Integer itemType,
            @RequestParam(name = "status", required = false) Integer status) {
        Page<GameItem> result = gameResourceAdminService.pageItems(page, size, keyword, itemType, status);
        return Result.success(PageResult.of(result.getRecords(), result.getCurrent(), result.getSize(), result.getTotal()));
    }

    @AdminCheck
    @PostMapping("/admin/items")
    public Result<Void> createItem(@RequestBody GameItem item) {
        gameResourceAdminService.createItem(item);
        return Result.success(null);
    }

    @AdminCheck
    @PutMapping("/admin/items")
    public Result<Void> updateItem(@RequestBody GameItem item) {
        gameResourceAdminService.updateItem(item);
        return Result.success(null);
    }

    @AdminCheck
    @DeleteMapping("/admin/items/{id}")
    public Result<Void> disableItem(@PathVariable("id") Long id) {
        gameResourceAdminService.disableItem(id);
        return Result.success(null);
    }

    @AdminCheck
    @PostMapping("/admin/items/detail")
    public Result<Void> saveItemDetail(@RequestBody ItemDetail detail) {
        gameResourceAdminService.saveItemDetail(detail);
        return Result.success(null);
    }

    @AdminCheck
    @GetMapping("/admin/sign-in-rewards")
    public Result<List<SignInReward>> listSignInRewards() {
        return Result.success(gameResourceAdminService.listSignInRewards());
    }

    @AdminCheck
    @PostMapping("/admin/sign-in-rewards")
    public Result<Void> createSignInReward(@RequestBody SignInReward reward) {
        gameResourceAdminService.createSignInReward(reward);
        return Result.success(null);
    }

    @AdminCheck
    @PutMapping("/admin/sign-in-rewards")
    public Result<Void> updateSignInReward(@RequestBody SignInReward reward) {
        gameResourceAdminService.updateSignInReward(reward);
        return Result.success(null);
    }

    @AdminCheck
    @DeleteMapping("/admin/sign-in-rewards/{id}")
    public Result<Void> deleteSignInReward(@PathVariable("id") Long id) {
        gameResourceAdminService.deleteSignInReward(id);
        return Result.success(null);
    }
}
