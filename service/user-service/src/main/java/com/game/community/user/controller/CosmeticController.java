package com.game.community.user.controller;

import com.game.community.common.annotation.AdminCheck;
import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.cosmetic.BatchUserIdsDTO;
import com.game.community.model.dto.cosmetic.EquipCosmeticDTO;
import com.game.community.model.dto.cosmetic.SaveCosmeticDefDTO;
import com.game.community.model.dto.cosmetic.UnequipCosmeticDTO;
import com.game.community.model.dto.cosmetic.UseConsumableCosmeticDTO;
import com.game.community.model.vo.cosmetic.CosmeticDefVO;
import com.game.community.model.vo.cosmetic.UserCosmeticVO;
import com.game.community.model.vo.cosmetic.CosmeticItemStateVO;
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

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/user/cosmetic")
@RequiredArgsConstructor
public class CosmeticController {

    private final CosmeticService cosmeticService;
    private final UserQueryService userQueryService;

    @LoginCheck
    @GetMapping("/backpack")
    public Result<List<UserCosmeticVO>> backpack() {
        return Result.success(cosmeticService.listBackpack(UserThreadLocal.getUserId()));
    }

    @GetMapping("/decoration/{userId}")
    public Result<UserDecorationVO> decoration(@PathVariable("userId") Long userId) {
        return Result.success(cosmeticService.getDecoration(userId));
    }

    @PostMapping("/decorations/batch")
    public Result<Map<Long, UserDecorationVO>> batchDecorations(
            @Valid @RequestBody BatchUserIdsDTO dto) {
        // 前端及社交接口使用对外 accountId；装扮表按内部 userId 关联。
        // 先批量完成 accountId -> userId 映射，再批量读取装扮，避免 N 次用户查询和 N 次装扮查询。
        List<Long> accountIds = dto.getUserIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Result<List<UserCardInternalVO>> usersResult =
                userQueryService.getUsersInternalByAccountIds(accountIds);
        List<UserCardInternalVO> users = usersResult == null || usersResult.getData() == null
                ? List.of()
                : usersResult.getData();
        Map<Long, UserDecorationVO> decorations = cosmeticService.batchDecorations(users.stream()
                .map(UserCardInternalVO::getUserId)
                .filter(Objects::nonNull)
                .toList());
        Map<Long, UserDecorationVO> result = new java.util.LinkedHashMap<>();
        for (UserCardInternalVO user : users) {
            UserDecorationVO decoration = decorations.get(user.getUserId());
            if (user.getAccountId() != null && decoration != null) {
                result.put(user.getAccountId(), decoration);
            }
        }
        return Result.success(result);
    }

    @LoginCheck
    @PutMapping("/equip")
    public Result<Void> equip(@Valid @RequestBody EquipCosmeticDTO dto) {
        cosmeticService.equip(UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }

    @LoginCheck
    @PutMapping("/unequip")
    public Result<Void> unequip(@Valid @RequestBody UnequipCosmeticDTO dto) {
        cosmeticService.unequip(UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }

    @LoginCheck
    @PostMapping("/use")
    public Result<Void> use(@Valid @RequestBody UseConsumableCosmeticDTO dto) {
        cosmeticService.useConsumable(UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }

    @AdminCheck
    @GetMapping("/admin/def/page")
    public PageResult<CosmeticDefVO> pageDefs(@RequestParam(value = "page", defaultValue = "1") Long page,
                                              @RequestParam(value = "size", defaultValue = "20") Long size,
                                              @RequestParam(value = "category", required = false) String category,
                                              @RequestParam(value = "status", required = false) Integer status) {
        return cosmeticService.pageDefs(page, size, category, status);
    }

    @AdminCheck
    @PostMapping("/admin/def")
    public Result<CosmeticDefVO> saveDef(@Valid @RequestBody SaveCosmeticDefDTO dto) {
        return Result.success(cosmeticService.saveDef(dto));
    }
}
