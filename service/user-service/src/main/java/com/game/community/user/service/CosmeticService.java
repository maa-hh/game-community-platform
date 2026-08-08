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

    PageResult<CosmeticDefVO> pageDefs(Long page, Long size, String category, Integer status);

    CosmeticDefVO saveDef(SaveCosmeticDefDTO dto);

    PageResult<UserCosmeticVO> pageBackpack(Long userId, Long page, Long size,
                                             String effectMode, String category,
                                             Boolean equipped, String state, String keyword);

    UserDecorationVO getDecoration(Long userId);

    CosmeticGrantResultVO grantCosmetic(GrantCosmeticDTO dto);

    CosmeticPurchaseCheckVO checkOwnershipBlock(Long userId, String cosmeticCode);

    CosmeticItemStateVO getItemState(Long userId, String cosmeticCode);

    /** 按对外 accountId 批量查询装扮，内部完成 accountId -> userId 映射。 */
    Map<Long, UserDecorationVO> batchDecorationsByAccountIds(List<Long> accountIds);

    void equip(Long userId, EquipCosmeticDTO dto);

    void unequip(Long userId, UnequipCosmeticDTO dto);

    void useConsumable(Long userId, UseConsumableCosmeticDTO dto);
}
