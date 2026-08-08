package com.game.community.user.feign;

import com.game.community.model.base.Result;
import com.game.community.model.dto.cosmetic.BatchUserIdsDTO;
import com.game.community.model.dto.cosmetic.GrantCosmeticDTO;
import com.game.community.model.vo.cosmetic.CosmeticGrantResultVO;
import com.game.community.model.vo.cosmetic.CosmeticItemStateVO;
import com.game.community.model.vo.cosmetic.CosmeticPurchaseCheckVO;
import com.game.community.model.vo.cosmetic.UserDecorationVO;
import com.game.community.model.vo.user.UserAccountVO;
import com.game.community.model.vo.user.UserAuditTaskBriefVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.model.vo.user.UserCardVO;
import com.game.community.user.service.CosmeticService;
import com.game.community.user.service.UserAccountService;
import com.game.community.user.service.UserFieldAuditTaskService;
import com.game.community.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户服务 Feign 内部接口（供其他微服务调用）
 */
@RestController
@RequestMapping("/feign/user")
@RequiredArgsConstructor
public class UserFeignController {

    private final UserQueryService userQueryService;
    private final UserAccountService userAccountService;
    private final UserFieldAuditTaskService userFieldAuditTaskService;
    private final CosmeticService cosmeticService;

    @GetMapping("/ids")
    public Result<List<UserCardInternalVO>> getUsersByUserIds(@RequestParam("ids") List<Long> ids) {
        return userQueryService.getUsersByUserIds(ids);
    }

    @GetMapping("/account-ids")
    public Result<List<UserCardVO>> getUsersByAccountIds(@RequestParam("ids") List<Long> accountIds) {
        return userQueryService.getUsersByAccountIds(accountIds);
    }

    @GetMapping("/by-account/{accountId}")
    public Result<UserCardInternalVO> getUserByAccountId(@PathVariable("accountId") Long accountId) {
        return userQueryService.getUserInternalByAccountId(accountId);
    }

    /** 封禁用户（供审核服务等调用） */
    @PostMapping("/account/{accountId}/ban")
    public Result<Void> banUser(@PathVariable("accountId") Long accountId,
                                @RequestParam("reason") String reason,
                                @RequestParam(value = "durationHours", required = false) Integer durationHours) {
        return userAccountService.banUserByAccountId(accountId, reason, durationHours, null);
    }

    /** 解封用户 */
    @PostMapping("/account/{accountId}/unban")
    public Result<Void> unbanUser(@PathVariable("accountId") Long accountId) {
        return userAccountService.unbanUserByAccountId(accountId, null);
    }

    /** 查询用户账户状态 */
    @GetMapping("/{userId}/account")
    public Result<UserAccountVO> getUserAccount(@PathVariable("userId") Long userId) {
        return userQueryService.getUserAccountVO(userId);
    }

    @PostMapping("/audit-tasks/{taskId}/approve")
    public Result<Void> approveProfileManualAudit(@PathVariable("taskId") Long taskId) {
        return userFieldAuditTaskService.approveHumanReview(taskId);
    }

    @PostMapping("/audit-tasks/{taskId}/reject")
    public Result<Void> rejectProfileManualAudit(@PathVariable("taskId") Long taskId,
                                               @RequestParam(value = "reason", required = false) String reason) {
        return userFieldAuditTaskService.rejectHumanReview(taskId, reason);
    }

    @GetMapping("/audit-tasks/{taskId}")
    public Result<UserAuditTaskBriefVO> getAuditTaskBrief(@PathVariable("taskId") Long taskId) {
        return Result.success(userFieldAuditTaskService.getAuditTaskBrief(taskId));
    }

    @PostMapping("/cosmetic/grant")
    public Result<CosmeticGrantResultVO> grantCosmetic(@RequestBody GrantCosmeticDTO dto) {
        return Result.success(cosmeticService.grantCosmetic(dto));
    }

    @GetMapping("/cosmetic/purchase-check")
    public Result<CosmeticPurchaseCheckVO> checkCosmeticOwnership(@RequestParam("userId") Long userId,
                                                                  @RequestParam("cosmeticCode") String cosmeticCode) {
        return Result.success(cosmeticService.checkOwnershipBlock(userId, cosmeticCode));
    }

    @GetMapping("/cosmetic/state")
    public Result<CosmeticItemStateVO> getCosmeticItemState(@RequestParam("userId") Long userId,
                                                            @RequestParam("cosmeticCode") String cosmeticCode) {
        return Result.success(cosmeticService.getItemState(userId, cosmeticCode));
    }

    @PostMapping("/cosmetic/decorations/batch")
    public Result<Map<Long, UserDecorationVO>> batchDecorations(@RequestBody BatchUserIdsDTO dto) {
        return Result.success(cosmeticService.batchDecorations(dto.getAccountIds()));
    }

    @PostMapping("/{userId}/steam-account")
    public Result<Void> updateSteamAccount(@PathVariable("userId") Long userId,
                                           @RequestParam("steamAccount") String steamAccount) {
        userAccountService.updateSteamAccount(userId, steamAccount);
        return Result.success(null);
    }
}
