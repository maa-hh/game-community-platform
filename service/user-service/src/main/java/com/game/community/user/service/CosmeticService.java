package com.game.community.user.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.cosmetic.BatchUserIdsDTO;
import com.game.community.model.dto.cosmetic.EquipCosmeticDTO;
import com.game.community.model.dto.cosmetic.GrantCosmeticDTO;
import com.game.community.model.dto.cosmetic.SaveCosmeticDefDTO;
import com.game.community.model.dto.cosmetic.UnequipCosmeticDTO;
import com.game.community.model.dto.cosmetic.UseConsumableCosmeticDTO;
import com.game.community.model.vo.cosmetic.CosmeticDefVO;
import com.game.community.model.vo.cosmetic.CosmeticGrantResultVO;
import com.game.community.model.vo.cosmetic.CosmeticItemStateVO;
import com.game.community.model.vo.cosmetic.CosmeticPurchaseCheckVO;
import com.game.community.model.vo.cosmetic.UserCosmeticVO;
import com.game.community.model.vo.cosmetic.UserDecorationVO;

import java.util.List;
import java.util.Map;

public interface CosmeticService {

    /** 分页查询后台装扮定义。 */
    PageResult<CosmeticDefVO> pageDefs(Long page, Long size, String category, Integer status);

    /** 新增或更新装扮定义。 */
    CosmeticDefVO saveDef(SaveCosmeticDefDTO dto);

    /** 分页查询当前用户已拥有的装扮。 */
    PageResult<UserCosmeticVO> pageBackpack(Long userId, Long page, Long size,
                                             String effectMode, String category,
                                             Boolean equipped, String state, String keyword);

    /** 查询用户当前已装备的装扮。 */
    UserDecorationVO getDecoration(Long userId);

    /** 按对外 accountId 查询用户当前已装备的装扮。 */
    UserDecorationVO getDecorationByAccountId(Long accountId);

    /** 发放装扮并返回发放结果。 */
    CosmeticGrantResultVO grantCosmetic(GrantCosmeticDTO dto);

    /** 判断指定装扮是否阻止再次购买。 */
    CosmeticPurchaseCheckVO checkOwnershipBlock(Long userId, String cosmeticCode);

    /** 查询用户对指定装扮的拥有状态。 */
    CosmeticItemStateVO getItemState(Long userId, String cosmeticCode);

    /** 按对外 accountId 批量查询装扮，内部完成 accountId -> userId 映射。 */
    Map<Long, UserDecorationVO> batchDecorationsByAccountIds(List<Long> accountIds);

    /** 装备用户拥有的装备类装扮。 */
    void equip(Long userId, EquipCosmeticDTO dto);

    /** 卸下用户当前槽位的装扮。 */
    void unequip(Long userId, UnequipCosmeticDTO dto);

    /** 使用消耗类装扮并扣减数量。 */
    void useConsumable(Long userId, UseConsumableCosmeticDTO dto);
}
