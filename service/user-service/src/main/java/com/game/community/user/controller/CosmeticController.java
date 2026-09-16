package com.game.community.user.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.common.constant.cosmetic.CosmeticConstants;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.cosmetic.BatchUserIdsDTO;
import com.game.community.model.dto.cosmetic.EquipCosmeticDTO;
import com.game.community.model.dto.cosmetic.SaveCosmeticDefDTO;
import com.game.community.model.dto.cosmetic.UnequipCosmeticDTO;
import com.game.community.model.dto.cosmetic.UseConsumableCosmeticDTO;
import com.game.community.model.vo.cosmetic.CosmeticDefVO;
import com.game.community.model.vo.cosmetic.CosmeticItemStateVO;
import com.game.community.model.vo.cosmetic.UserCosmeticVO;
import com.game.community.model.vo.cosmetic.UserDecorationVO;
import com.game.community.user.service.CosmeticService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 用户装扮背包、装备和后台定义管理的 HTTP 入口。 */
@RestController
@RequestMapping("/user/cosmetic")
@RequiredArgsConstructor
public class CosmeticController {

    private final CosmeticService cosmeticService;

    /** 按条件分页查询当前登录用户的装扮背包。 */
    @LoginCheck
    @GetMapping("/backpack/page")
    public PageResult<UserCosmeticVO> backpackPage(
            @RequestParam(value = "page", defaultValue = CosmeticConstants.FIRST_PAGE_TEXT) Long page,
            @RequestParam(value = "size", defaultValue = CosmeticConstants.DEFAULT_PAGE_SIZE_TEXT) Long size,
            @RequestParam(value = "effectMode", required = false) String effectMode,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "equipped", required = false) Boolean equipped,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "keyword", required = false) String keyword) {
        return cosmeticService.pageBackpack(UserThreadLocal.getUserId(), page, size,
                effectMode, category, equipped, state, keyword);
    }

    /** 按对外 accountId 查询用户当前装扮和生效效果。 */
    @LoginCheck
    @GetMapping("/decoration/{accountId}")
    public Result<UserDecorationVO> decoration(@PathVariable("accountId") Long accountId) {
        return Result.success(cosmeticService.getDecorationByAccountId(accountId));
    }

    /** 批量查询多个 accountId 的装扮展示数据。 */
    @PostMapping("/decorations/batch")
    public Result<Map<Long, UserDecorationVO>> batchDecorations(
            @Valid @RequestBody BatchUserIdsDTO dto) {
        return Result.success(cosmeticService.batchDecorationsByAccountIds(dto.getAccountIds()));
    }

    /** 装备指定槽位的用户装扮。 */
    @LoginCheck
    @PutMapping("/equip")
    public Result<Void> equip(@Valid @RequestBody EquipCosmeticDTO dto) {
        cosmeticService.equip(UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }

    /** 卸下指定槽位的当前装扮。 */
    @LoginCheck
    @PutMapping("/unequip")
    public Result<Void> unequip(@Valid @RequestBody UnequipCosmeticDTO dto) {
        cosmeticService.unequip(UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }

    /** 使用一个消耗类装扮并记录使用结果。 */
    @LoginCheck
    @PostMapping("/use")
    public Result<Void> use(@Valid @RequestBody UseConsumableCosmeticDTO dto) {
        cosmeticService.useConsumable(UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }

    /** 分页查询后台装扮定义。 */
    @AdminCheck
    @GetMapping("/admin/def/page")
    public PageResult<CosmeticDefVO> pageDefs(@RequestParam(value = "page", defaultValue = CosmeticConstants.FIRST_PAGE_TEXT) Long page,
                                              @RequestParam(value = "size", defaultValue = CosmeticConstants.DEFAULT_PAGE_SIZE_TEXT) Long size,
                                              @RequestParam(value = "category", required = false) String category,
                                              @RequestParam(value = "status", required = false) Integer status) {
        return cosmeticService.pageDefs(page, size, category, status);
    }

    /** 新增或更新后台装扮定义并刷新共享缓存。 */
    @AdminCheck
    @PostMapping("/admin/def")
    public Result<CosmeticDefVO> saveDef(@Valid @RequestBody SaveCosmeticDefDTO dto) {
        return Result.success(cosmeticService.saveDef(dto));
    }
}
