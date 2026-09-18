package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.model.entity.cosmetic.UserCosmetic;
import com.game.community.common.constant.cosmetic.CosmeticConstants;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserCosmeticMapper extends BaseMapper<UserCosmetic> {

    /** 按筛选条件分页查询用户背包。 */
    IPage<UserCosmetic> selectBackpackPage(
            Page<UserCosmetic> page,
            @Param("userId") Long userId,
            @Param("effectMode") String effectMode,
            @Param("category") String category,
            @Param("equipped") Boolean equipped,
            @Param("state") String state,
            @Param("keyword") String keyword);

    /** 查询用户指定装扮的库存记录。 */
    @Select("SELECT * FROM t_user_cosmetic WHERE user_id = #{userId} AND cosmetic_code = #{code} LIMIT 1")
    /** 查询用户指定装扮的库存记录。 */
    UserCosmetic selectByUserAndCode(@Param("userId") Long userId, @Param("code") String code);

    /** 批量查询用户装扮库存，避免商品页逐个查询。 */
    @Select({
            "<script>",
            "SELECT * FROM t_user_cosmetic WHERE user_id = #{userId} AND cosmetic_code IN",
            "<foreach collection='codes' item='code' open='(' separator=',' close=')'>",
            "#{code}",
            "</foreach>",
            "</script>"
    })
    List<UserCosmetic> selectByUserAndCodes(@Param("userId") Long userId,
                                            @Param("codes") List<String> codes);

    /** 幂等增加用户装扮库存。 */
    @Insert("INSERT INTO t_user_cosmetic "
            + "(user_id, cosmetic_code, quantity, source_type, source_ref, acquired_at, create_time, update_time) "
            + "VALUES (#{userId}, #{code}, #{quantity}, #{sourceType}, #{sourceRef}, NOW(), NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE "
            + "quantity = CASE WHEN #{stackable} = " + CosmeticConstants.STACKABLE
            + " THEN quantity + #{quantity} ELSE quantity END, "
            + "update_time = NOW()")
    int upsertOwned(@Param("userId") Long userId, @Param("code") String code,
                    @Param("quantity") int quantity, @Param("sourceType") String sourceType,
                    @Param("sourceRef") String sourceRef, @Param("stackable") int stackable);

    /** 原子增减装扮数量，数量不足时更新失败。 */
    @Update("UPDATE t_user_cosmetic SET quantity = quantity + #{delta}, update_time = NOW() "
            + "WHERE user_id = #{userId} AND cosmetic_code = #{code} AND quantity + #{delta} >= 0")
    /** 原子增减装扮库存，禁止结果小于零。 */
    int increaseQuantity(@Param("userId") Long userId, @Param("code") String code, @Param("delta") int delta);
}
