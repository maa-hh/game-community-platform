package com.game.community.shop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.game.community.model.entity.shop.ShopPointsLedger;

@Mapper
public interface ShopPointsLedgerMapper extends BaseMapper<ShopPointsLedger> {

    @Select("SELECT * FROM t_shop_points_ledger WHERE biz_type = #{bizType} AND biz_ref = #{bizRef} LIMIT 1")
    ShopPointsLedger selectByBizRef(@Param("bizType") String bizType, @Param("bizRef") String bizRef);
}
