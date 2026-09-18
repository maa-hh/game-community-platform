package com.game.community.feign;

import com.game.community.model.base.Result;
import com.game.community.model.dto.cosmetic.BatchUserIdsDTO;
import com.game.community.model.dto.cosmetic.BatchCosmeticStateDTO;
import com.game.community.model.dto.cosmetic.GrantCosmeticDTO;
import com.game.community.model.vo.cosmetic.CosmeticGrantResultVO;
import com.game.community.model.vo.cosmetic.CosmeticItemStateVO;
import com.game.community.model.vo.cosmetic.CosmeticPurchaseCheckVO;
import com.game.community.model.vo.cosmetic.UserDecorationVO;
import com.game.community.model.vo.user.UserAccountVO;
import com.game.community.model.vo.user.UserAuditTaskBriefVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.model.vo.user.UserCardVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 用户服务 Feign 客户端（供其他微服务调用）
 */
@FeignClient(name = "user-service", contextId = "userFeignClient", path = "/feign/user")
public interface UserFeignClient {

    @GetMapping("/ids")
    Result<List<UserCardInternalVO>> getUsersByUserIds(@RequestParam("ids") List<Long> ids);

    @GetMapping("/account-ids")
    Result<List<UserCardVO>> getUsersByAccountIds(@RequestParam("ids") List<Long> accountIds);

    @GetMapping("/by-account/{accountId}")
    Result<UserCardInternalVO> getUserByAccountId(@PathVariable("accountId") Long accountId);

    /** 封禁用户（供审核服务等调用） */
    @PostMapping("/account/{accountId}/ban")
    Result<Void> banUser(@PathVariable("accountId") Long accountId,
                         @RequestParam("reason") String reason,
                         @RequestParam(value = "durationHours", required = false) Integer durationHours);

    /** 解封用户 */
    @PostMapping("/account/{accountId}/unban")
    Result<Void> unbanUser(@PathVariable("accountId") Long accountId);

    /** 查询用户账户状态 */
    @GetMapping("/{userId}/account")
    Result<UserAccountVO> getUserAccount(@PathVariable("userId") Long userId);

    @PostMapping("/audit-tasks/{taskId}/approve")
    Result<Void> approveProfileManualAudit(@PathVariable("taskId") Long taskId);

    @PostMapping("/audit-tasks/{taskId}/reject")
    Result<Void> rejectProfileManualAudit(@PathVariable("taskId") Long taskId,
                                          @RequestParam(value = "reason", required = false) String reason);

    @GetMapping("/audit-tasks/{taskId}")
    Result<UserAuditTaskBriefVO> getAuditTaskBrief(@PathVariable("taskId") Long taskId);

    @PostMapping("/cosmetic/grant")
    Result<CosmeticGrantResultVO> grantCosmetic(@RequestBody GrantCosmeticDTO dto);

    @GetMapping("/cosmetic/purchase-check")
    Result<CosmeticPurchaseCheckVO> checkCosmeticOwnership(@RequestParam("userId") Long userId,
                                                            @RequestParam("cosmeticCode") String cosmeticCode);

    @GetMapping("/cosmetic/state")
    Result<CosmeticItemStateVO> getCosmeticItemState(@RequestParam("userId") Long userId,
                                                     @RequestParam("cosmeticCode") String cosmeticCode);

    @PostMapping("/cosmetic/state/batch")
    Result<Map<String, CosmeticItemStateVO>> getCosmeticItemStates(@RequestBody BatchCosmeticStateDTO dto);

    @PostMapping("/cosmetic/decorations/batch")
    Result<Map<Long, UserDecorationVO>> batchDecorations(@RequestBody BatchUserIdsDTO dto);

    /** 更新用户 Steam 账号（供 steam-service 绑定/解绑） */
    @PostMapping("/{userId}/steam-account")
    Result<Void> updateSteamAccount(@PathVariable("userId") Long userId,
                                    @RequestParam("steamAccount") String steamAccount);
}
