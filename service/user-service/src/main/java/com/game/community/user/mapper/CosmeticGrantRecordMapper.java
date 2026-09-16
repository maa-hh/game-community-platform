package com.game.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.cosmetic.CosmeticGrantRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CosmeticGrantRecordMapper extends BaseMapper<CosmeticGrantRecord> {

    /** 按业务订单号查询发放幂等记录。 */
    @Select("SELECT * FROM t_cosmetic_grant_record WHERE order_no = #{orderNo} LIMIT 1")
    /** 按订单号查询发放记录，用于实现跨服务重试幂等。 */
    CosmeticGrantRecord selectByOrderNo(String orderNo);
}
