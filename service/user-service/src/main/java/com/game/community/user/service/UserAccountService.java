package com.game.community.user.service;

import com.game.community.model.base.Result;
import com.game.community.model.dto.user.CancelAccountDTO;
import com.game.community.model.entity.user.UserAccount;
import com.game.community.model.vo.user.SendCodeVO;

/**
 * 用户账户生命周期服务：封禁、解封、注销、撤销注销
 * <p>
 * 账号状态不做定时扫描：在登录、查资料、交互时被动检测并回写。
 */
public interface UserAccountService {

    /** 申请注销账号，进入冷静期（需邮箱验证码） */
    Result<Void> cancelAccount(CancelAccountDTO dto);

    /** 向当前绑定邮箱发送注销验证码（需登录） */
    Result<SendCodeVO> sendCancelAccountCode();

    /** 冷静期内登录可撤销注销 */
    Result<Void> revokeCancel();

    /** 冷静期内登录撤销注销（认证流程内部调用，按 userId） */
    void revokeCancel(Long userId);

    /** 封禁用户（管理员 / Feign），durationHours 为 null 或 0 表示永久封禁 */
    Result<Void> banUser(Long userId, String reason, Integer durationHours, Long operatorId);

    /** 解封用户（管理员 / Feign） */
    Result<Void> unbanUser(Long userId, Long operatorId);

    /**
     * 被动刷新账号状态并返回最新实体：
     * 过期封禁 → 正常；冷静期已结束 → 完成注销。
     */
    UserAccount refreshStatus(UserAccount account);

    /** 按 userId 加载并被动刷新状态 */
    UserAccount refreshStatus(Long userId);

    /** 更新 Steam 账号字段（Feign / steam-service 绑定解绑） */
    void updateSteamAccount(Long userId, String steamAccount);

    /**
     * 断言可登录/可刷新令牌：已注销或仍封禁则抛错；
     * 冷静期中（CANCELLING）不抛，由登录流程决定是否撤销。
     */
    void assertLoginAllowed(UserAccount account);

    /** 断言可改资料等交互：仅正常状态允许 */
    void assertEditable(UserAccount account);
}
