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
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.user.service.CosmeticService;
import com.game.community.user.service.UserQueryService;
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

@RestController
@RequestMapping("/user/cosmetic")
@RequiredArgsConstructor
public class CosmeticController {

    private final CosmeticService cosmeticService;
    private final UserQueryService userQueryService;

    @LoginCheck
    /** 执行 backpackPage 对应的业务处理。 */
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

    /** 执行 decoration 对应的业务处理。 */
    @GetMapping("/decoration/{accountId}")
    public Result<UserDecorationVO> decoration(@PathVariable("accountId") Long accountId) {
        UserCardInternalVO user = requireUserByAccountId(accountId);
        return Result.success(cosmeticService.getDecoration(user.getUserId()));
    }

    /** 执行 batchDecorations 对应的业务处理。 */
    @PostMapping("/decorations/batch")
    public Result<Map<Long, UserDecorationVO>> batchDecorations(
            @Valid @RequestBody BatchUserIdsDTO dto) {
        return Result.success(cosmeticService.batchDecorationsByAccountIds(dto.getAccountIds()));
    }

    /** 执行 requireUserByAccountId 对应的业务处理。 */
    private UserCardInternalVO requireUserByAccountId(Long accountId) {
        Result<UserCardInternalVO> result = userQueryService.getUserInternalByAccountId(accountId);
        if (result == null || result.getData() == null) {
            throw new com.game.community.common.exception.BusinessException("用户不存在");
        }
        return result.getData();
    }

    @LoginCheck
    /** 执行 equip 对应的业务处理。 */
    @PutMapping("/equip")
    public Result<Void> equip(@Valid @RequestBody EquipCosmeticDTO dto) {
        cosmeticService.equip(UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }

    @LoginCheck
    /** 执行 unequip 对应的业务处理。 */
    @PutMapping("/unequip")
    public Result<Void> unequip(@Valid @RequestBody UnequipCosmeticDTO dto) {
        cosmeticService.unequip(UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }

    @LoginCheck
    /** 执行 use 对应的业务处理。 */
    @PostMapping("/use")
    public Result<Void> use(@Valid @RequestBody UseConsumableCosmeticDTO dto) {
        cosmeticService.useConsumable(UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }

    @AdminCheck
    @GetMapping("/admin/def/page")
    public PageResult<CosmeticDefVO> pageDefs(@RequestParam(value = "page", defaultValue = CosmeticConstants.FIRST_PAGE_TEXT) Long page,
                                              @RequestParam(value = "size", defaultValue = CosmeticConstants.DEFAULT_PAGE_SIZE_TEXT) Long size,
                                              @RequestParam(value = "category", required = false) String category,
                                              @RequestParam(value = "status", required = false) Integer status) {
        return cosmeticService.pageDefs(page, size, category, status);
    }

    @AdminCheck
    /** 执行 saveDef 对应的业务处理。 */
    @PostMapping("/admin/def")
    public Result<CosmeticDefVO> saveDef(@Valid @RequestBody SaveCosmeticDefDTO dto) {
        return Result.success(cosmeticService.saveDef(dto));
    }
}
