package com.game.community.user.feign;

import com.game.community.common.constant.user.UserConstants;
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
import jakarta.validation.Valid;
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

    /** 按内部 userId 批量查询供服务间使用的用户卡片。 */
    @GetMapping("/ids")
    public Result<List<UserCardInternalVO>> getUsersByUserIds(@RequestParam("ids") List<Long> ids) {
        return userQueryService.getUsersByUserIds(ids);
    }

    /** 按对外 accountId 批量查询服务间用户卡片。 */
    @GetMapping("/account-ids")
    public Result<List<UserCardVO>> getUsersByAccountIds(@RequestParam("ids") List<Long> accountIds) {
        return userQueryService.getUsersByAccountIds(accountIds);
    }

    /** 按 accountId 查询含内部 userId 的服务间用户卡片。 */
    @GetMapping("/by-account/{accountId}")
    public Result<UserCardInternalVO> getUserByAccountId(@PathVariable("accountId") Long accountId) {
        return userQueryService.getUserInternalByAccountId(accountId);
    }

    /** 封禁用户（供审核服务等调用） */
    @PostMapping("/account/{accountId}/ban")
    public Result<Void> banUser(@PathVariable("accountId") Long accountId,
                                @RequestParam("reason") String reason,
                                @RequestParam(value = "durationHours", required = false) Integer durationHours) {
        return userAccountService.banUserByAccountId(accountId, reason, durationHours,
                UserConstants.SYSTEM_OPERATOR_ID);
    }

    /** 解封用户 */
    @PostMapping("/account/{accountId}/unban")
    public Result<Void> unbanUser(@PathVariable("accountId") Long accountId) {
        return userAccountService.unbanUserByAccountId(accountId, UserConstants.SYSTEM_OPERATOR_ID);
    }

    /** 查询用户账户状态 */
    @GetMapping("/{userId}/account")
    public Result<UserAccountVO> getUserAccount(@PathVariable("userId") Long userId) {
        return userQueryService.getUserAccountVO(userId);
    }

    /** 接收人工审核通过命令并回写用户资料。 */
    @PostMapping("/audit-tasks/{taskId}/approve")
    public Result<Void> approveProfileManualAudit(@PathVariable("taskId") Long taskId) {
        return userFieldAuditTaskService.approveHumanReview(taskId);
    }

    /** 接收人工审核拒绝命令并释放待审字段。 */
    @PostMapping("/audit-tasks/{taskId}/reject")
    public Result<Void> rejectProfileManualAudit(@PathVariable("taskId") Long taskId,
                                               @RequestParam(value = "reason", required = false) String reason) {
        return userFieldAuditTaskService.rejectHumanReview(taskId, reason);
    }

    /** 查询人工审核任务摘要。 */
    @GetMapping("/audit-tasks/{taskId}")
    public Result<UserAuditTaskBriefVO> getAuditTaskBrief(@PathVariable("taskId") Long taskId) {
        return Result.success(userFieldAuditTaskService.getAuditTaskBrief(taskId));
    }

    /** 按订单号幂等发放用户装扮。 */
    @PostMapping("/cosmetic/grant")
    public Result<CosmeticGrantResultVO> grantCosmetic(@Valid @RequestBody GrantCosmeticDTO dto) {
        return Result.success(cosmeticService.grantCosmetic(dto));
    }

    /** 查询购买前的装扮拥有状态。 */
    @GetMapping("/cosmetic/purchase-check")
    public Result<CosmeticPurchaseCheckVO> checkCosmeticOwnership(@RequestParam("userId") Long userId,
                                                                  @RequestParam("cosmeticCode") String cosmeticCode) {
        return Result.success(cosmeticService.checkOwnershipBlock(userId, cosmeticCode));
    }

    /** 查询用户对指定装扮的拥有和装备状态。 */
    @GetMapping("/cosmetic/state")
    public Result<CosmeticItemStateVO> getCosmeticItemState(@RequestParam("userId") Long userId,
                                                            @RequestParam("cosmeticCode") String cosmeticCode) {
        return Result.success(cosmeticService.getItemState(userId, cosmeticCode));
    }

    /** 批量查询多个 accountId 的装扮展示数据。 */
    @PostMapping("/cosmetic/decorations/batch")
    public Result<Map<Long, UserDecorationVO>> batchDecorations(@Valid @RequestBody BatchUserIdsDTO dto) {
        return Result.success(cosmeticService.batchDecorationsByAccountIds(dto.getAccountIds()));
    }

    /** 供 Steam 服务更新用户绑定的 Steam 账号。 */
    @PostMapping("/{userId}/steam-account")
    public Result<Void> updateSteamAccount(@PathVariable("userId") Long userId,
                                           @RequestParam("steamAccount") String steamAccount) {
        userAccountService.updateSteamAccount(userId, steamAccount);
        return Result.success(null);
    }
}
